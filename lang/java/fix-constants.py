#!/usr/bin/env python3
"""Fix Cosmopolitan runtime C constants left unresolved in OpenJDK gensrc.

Only value-bearing syntactic regions are rewritten:
  * RHS of `static final int ... = ...;`
  * arguments of generated `new OptionKey(...)`
This intentionally avoids global token replacement, so Java identifiers such as
`StandardSocketOptions.SO_KEEPALIVE` are never touched.
"""
from __future__ import annotations
import argparse
import os
import pathlib
import re
import subprocess
import tempfile

DEFAULT_CONSTANTS = [
    'O_APPEND','O_CREAT','O_EXCL','O_TRUNC','O_SYNC','O_NOFOLLOW',
    'R_OK','W_OK','X_OK',
    'ENOENT','ENXIO','EACCES','EEXIST','ENOTDIR','EINVAL','EXDEV','EISDIR',
    'ENOTEMPTY','ENOSPC','EAGAIN','ENOSYS','ELOOP','EROFS','ENODATA','ERANGE','EMFILE',
    'AT_FDCWD','AT_SYMLINK_NOFOLLOW','AT_REMOVEDIR','POSIX_FADV_NOREUSE',
    'SOL_SOCKET','SO_BROADCAST','SO_KEEPALIVE','SO_LINGER','SO_SNDBUF','SO_RCVBUF',
    'SO_REUSEADDR','SO_REUSEPORT','SO_OOBINLINE',
    'IP_TOS','IP_MULTICAST_IF','IP_MULTICAST_TTL','IP_MULTICAST_LOOP',
    'IPV6_TCLASS','IPV6_MULTICAST_IF','IPV6_MULTICAST_HOPS','IPV6_MULTICAST_LOOP',
]

PROBE_HEADERS = r'''#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <cosmo.h>
#define P(x) printf(#x "=%lld\n", (long long)(x))
'''

def _apelink_darwin(cc: str, elf: pathlib.Path, ape: pathlib.Path) -> None:
    bin_dir = pathlib.Path(cc).resolve().parent
    apelink = bin_dir / 'apelink'
    ape_m1 = bin_dir / 'ape-m1.c'
    ape_arm = bin_dir / 'ape-aarch64.elf'
    if not (apelink.is_file() and ape_m1.is_file() and ape_arm.is_file()):
        raise SystemExit(f'need apelink/ape-m1/ape-aarch64.elf next to {cc}')
    subprocess.run(
        ['/bin/sh', str(apelink), '-l', str(ape_arm), '-M', str(ape_m1),
         '-o', str(ape), str(elf)],
        check=True,
    )
    ape.chmod(0o755)


def probe(cc: str, constants: list[str]) -> dict[str, str]:
    src = PROBE_HEADERS + 'int main(void) {\n' + ''.join(f'  P({c});\n' for c in constants) + '  return 0;\n}\n'
    with tempfile.TemporaryDirectory(prefix='openjdk-cosmo-constants-') as td:
        td = pathlib.Path(td)
        cfile, exe = td/'probe.c', td/'probe.com'
        cfile.write_text(src)
        if os.uname().sysname == 'Darwin':
            elf = td / 'probe.elf'
            subprocess.run([cc, '-g', '-o', str(elf), str(cfile)], check=True)
            _apelink_darwin(cc, elf, exe)
            out = subprocess.check_output(['/bin/sh', str(exe)], text=True)
        else:
            subprocess.run([cc, '-o', str(exe), str(cfile)], check=True)
            out = subprocess.check_output(['/bin/sh', str(exe)], text=True)
    vals = {}
    for line in out.splitlines():
        if '=' in line:
            k, v = line.split('=', 1)
            vals[k.strip()] = v.strip()
    missing = [c for c in constants if c not in vals]
    if missing:
        raise SystemExit('probe did not return: ' + ', '.join(missing))
    return vals

def token_replacer(values: dict[str, str]):
    names = sorted(values, key=len, reverse=True)
    rx = re.compile(r'(?<![A-Za-z0-9_$.])(' + '|'.join(map(re.escape, names)) + r')(?![A-Za-z0-9_])')
    def repl(m: re.Match[str]) -> str:
        return values[m.group(1)]
    return rx, repl

def rewrite_java(path: pathlib.Path, values: dict[str, str]) -> int:
    text = path.read_text()
    rx, repl = token_replacer(values)
    replacements = 0

    def rhs(m: re.Match[str]) -> str:
        nonlocal replacements
        body = m.group(2)
        new, n = rx.subn(repl, body)
        replacements += n
        return m.group(1) + new + m.group(3)

    # Generated constant definitions. DOTALL is intentional because the C
    # preprocessor output contains many blank lines between '=' and ';'.
    text = re.sub(r'(\bstatic\s+final\s+int\s+[A-Za-z_$][\w$]*\s*=)(.*?)(;)', rhs, text, flags=re.S)

    def option(m: re.Match[str]) -> str:
        nonlocal replacements
        body = m.group(2)
        new, n = rx.subn(repl, body)
        replacements += n
        return m.group(1) + new + m.group(3)

    # SocketOptionRegistry values are emitted as C constants inside OptionKey.
    text = re.sub(r'(\bnew\s+OptionKey\s*\()(.*?)(\))', option, text, flags=re.S)

    if replacements:
        path.write_text(text)
    return replacements

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--cc', required=True)
    ap.add_argument('--gensrc', required=True, type=pathlib.Path)
    ap.add_argument('--values-out', type=pathlib.Path)
    ns = ap.parse_args()
    values = probe(ns.cc, DEFAULT_CONSTANTS)
    if ns.values_out:
        ns.values_out.write_text(''.join(f'{k}={values[k]}\n' for k in DEFAULT_CONSTANTS))
    total = 0
    touched = []
    for path in ns.gensrc.rglob('*.java'):
        n = rewrite_java(path, values)
        if n:
            total += n
            touched.append((path, n))
    for path, n in touched:
        print(f'{path}: {n} replacements')
    print(f'total replacements: {total}')

if __name__ == '__main__':
    main()
