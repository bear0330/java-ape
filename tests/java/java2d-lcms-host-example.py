#!/usr/bin/env python3
"""Implement an OpenJDK LCMS Java2D color conversion with Pillow ImageCms."""
import base64
from io import BytesIO

from PIL import Image, ImageCms

from java2d_host_support import Java2DHostServer


class LcmsHost(Java2DHostServer):
    """Replaces the small LCMS native surface used by the Java2D example."""

    AWT_INIT_IDS = {
        "java/awt/image/ColorModel.initIDs()V",
        "java/awt/image/BufferedImage.initIDs()V",
        "java/awt/image/Raster.initIDs()V",
        "java/awt/image/SampleModel.initIDs()V",
        "sun/awt/image/ByteComponentRaster.initIDs()V",
    }
    LOAD_PROFILE = "sun/java2d/cmm/lcms/LCMS.loadProfileNative([BLjava/lang/Object;)J"
    GET_PROFILE_DATA = "sun/java2d/cmm/lcms/LCMS.getProfileDataNative(J)[B"
    GET_TAG = "sun/java2d/cmm/lcms/LCMS.getTagNative(JI)[B"
    CREATE_TRANSFORM = "sun/java2d/cmm/lcms/LCMS.createNativeTransform([JIIILjava/lang/Object;)J"
    COLOR_CONVERT = "sun/java2d/cmm/lcms/LCMS.colorConvert(JIIIIIILjava/lang/Object;Ljava/lang/Object;II)V"
    BYTE_BUFFER_TYPE = 0
    ICC_HEADER_SIZE = 128
    ICC_TAG_TABLE_OFFSET = 128
    ICC_TAG_ENTRY_SIZE = 12
    ICC_HEADER_TAG = int.from_bytes(b"head", "big")

    @staticmethod
    def _array_of_integers(value):
        if value["type"] != "long[]":
            raise ValueError(f"expected long[], received {value}")

        return value["value"]

    @staticmethod
    def _bytes_per_pixel(mode):
        return {"RGB": 3, "L": 1}[mode]

    @staticmethod
    def _decode_bytes(value):
        if value["type"] != "bytes" or value.get("encoding") != "base64":
            raise ValueError(f"expected base64 bytes, received {value}")

        return base64.b64decode(value["data"], validate=True)

    @staticmethod
    def _encode_bytes(value):
        return {
            "type": "bytes",
            "encoding": "base64",
            "data": base64.b64encode(value).decode("ascii"),
        }

    @staticmethod
    def _integer(value):
        if value["type"] not in ("int", "long"):
            raise ValueError(f"expected integer, received {value}")

        return value["value"]

    @staticmethod
    def _pixel_layout(formatter):
        # This example deliberately accepts only the two Java2D layouts it uses.
        if formatter == 0x00000419:
            return "RGB", "BGR"

        if formatter == 0x00000009:
            return "L", "L"

        raise ValueError(f"unsupported LCMS pixel formatter: 0x{formatter:08x}")

    def __init__(self):
        super().__init__("java2d-lcms-example-secret", ["java.native"])
        self.next_handle = 1
        self.profiles = {}
        self.transforms = {}
        self.converted_pixels = 0

    def replace_native_method(self, params):
        identity = f"{params['owner']}.{params['name']}{params['descriptor']}"
        if identity in self.AWT_INIT_IDS:
            return {"return": {"type": "void"}}

        arguments = params["arguments"]
        if identity == self.LOAD_PROFILE:
            return self._load_profile(arguments)

        if identity == self.GET_PROFILE_DATA:
            return self._get_profile_data(arguments)

        if identity == self.GET_TAG:
            return self._get_tag(arguments)

        if identity == self.CREATE_TRANSFORM:
            return self._create_transform(arguments)

        if identity == self.COLOR_CONVERT:
            return self._color_convert(arguments)

        raise ValueError(f"unsupported Java2D native method: {identity}")

    def verify_complete(self):
        if self.converted_pixels == 0:
            raise AssertionError("Python never performed a Java2D color conversion")

    def _allocate_handle(self):
        handle = self.next_handle
        self.next_handle += 1
        return handle

    def _color_convert(self, arguments):
        handle = self._integer(arguments[0])
        width = self._integer(arguments[1])
        height = self._integer(arguments[2])
        source_offset = self._integer(arguments[3])
        source_stride = self._integer(arguments[4])
        destination_offset = self._integer(arguments[5])
        destination_stride = self._integer(arguments[6])
        source = self._decode_bytes(arguments[7])
        destination = self._decode_bytes(arguments[8])
        source_type = self._integer(arguments[9])
        destination_type = self._integer(arguments[10])

        transform, input_mode, output_mode, input_raw_mode, output_raw_mode = self._transform(handle)
        if source_type != self.BYTE_BUFFER_TYPE or destination_type != self.BYTE_BUFFER_TYPE:
            raise ValueError("the example supports only byte-backed Java2D pixel buffers")

        source_bpp = self._bytes_per_pixel(input_mode)
        destination_bpp = self._bytes_per_pixel(output_mode)
        if source_stride != width * source_bpp or destination_stride != width * destination_bpp:
            raise ValueError("the example supports contiguous Java2D pixel rows")

        source_end = source_offset + source_stride * height
        if source_offset < 0 or source_end > len(source):
            raise ValueError("source Java2D pixel buffer is out of bounds")

        image = Image.frombytes(
            input_mode,
            (width, height),
            source[source_offset:source_end],
            "raw",
            input_raw_mode,
        )
        converted = ImageCms.applyTransform(image, transform)
        pixels = converted.tobytes("raw", output_raw_mode)

        destination_end = destination_offset + len(pixels)
        if destination_offset < 0 or destination_end > len(destination):
            raise ValueError("destination Java2D pixel buffer is out of bounds")

        result = bytearray(destination)
        result[destination_offset:destination_end] = pixels
        self.converted_pixels += width * height

        return {
            "return": {"type": "void"},
            "mutations": [{"argument": 8, "value": self._encode_bytes(result)}],
        }

    def _create_transform(self, arguments):
        profile_handles = self._array_of_integers(arguments[0])
        rendering_intent = self._integer(arguments[1])
        input_format = self._integer(arguments[2])
        output_format = self._integer(arguments[3])
        profiles = [self._profile(handle)[1] for handle in profile_handles]
        if len(profiles) != 2:
            raise ValueError("the example supports exactly one source and destination profile")

        input_mode, input_raw_mode = self._pixel_layout(input_format)
        output_mode, output_raw_mode = self._pixel_layout(output_format)
        transform = ImageCms.buildTransformFromOpenProfiles(
            profiles[0],
            profiles[1],
            input_mode,
            output_mode,
            renderingIntent=rendering_intent,
        )

        handle = self._allocate_handle()
        self.transforms[handle] = (
            transform,
            input_mode,
            output_mode,
            input_raw_mode,
            output_raw_mode,
        )
        return {"return": {"type": "long", "value": handle}}

    def _get_profile_data(self, arguments):
        handle = self._integer(arguments[0])
        profile_data, _profile = self._profile(handle)
        return {"return": self._encode_bytes(profile_data)}

    def _get_tag(self, arguments):
        profile_data, _profile = self._profile(self._integer(arguments[0]))
        signature = self._integer(arguments[1]) & 0xFFFFFFFF
        if signature == self.ICC_HEADER_TAG:
            return {"return": self._encode_bytes(profile_data[:self.ICC_HEADER_SIZE])}

        tag_data = self._profile_tag_data(profile_data, signature)
        return {"return": self._encode_bytes(tag_data)}

    def _load_profile(self, arguments):
        profile_data = self._decode_bytes(arguments[0])
        if len(profile_data) < 40 or profile_data[36:40] != b"acsp":
            raise ValueError("LCMS received an invalid ICC profile")

        handle = self._allocate_handle()
        profile = ImageCms.ImageCmsProfile(BytesIO(profile_data))
        self.profiles[handle] = (profile_data, profile)
        return {"return": {"type": "long", "value": handle}}

    def _profile(self, handle):
        profile = self.profiles.get(handle)
        if profile is None:
            raise ValueError(f"unknown ICC profile handle: {handle}")

        return profile

    def _profile_tag_data(self, profile_data, signature):
        tag_count = int.from_bytes(
            profile_data[self.ICC_TAG_TABLE_OFFSET:self.ICC_TAG_TABLE_OFFSET + 4],
            "big",
        )
        for index in range(tag_count):
            entry_offset = self.ICC_TAG_TABLE_OFFSET + 4 + index * self.ICC_TAG_ENTRY_SIZE
            entry_end = entry_offset + self.ICC_TAG_ENTRY_SIZE
            if entry_end > len(profile_data):
                break

            tag = int.from_bytes(profile_data[entry_offset:entry_offset + 4], "big")
            data_offset = int.from_bytes(profile_data[entry_offset + 4:entry_offset + 8], "big")
            data_length = int.from_bytes(profile_data[entry_offset + 8:entry_end], "big")
            data_end = data_offset + data_length
            if tag == signature and data_end <= len(profile_data):
                return profile_data[data_offset:data_end]

        raise ValueError(f"ICC profile does not contain tag: 0x{signature:08x}")

    def _transform(self, handle):
        transform = self.transforms.get(handle)
        if transform is None:
            raise ValueError(f"unknown ICC transform handle: {handle}")

        return transform


if __name__ == "__main__":
    LcmsHost().serve_from_command_line()
