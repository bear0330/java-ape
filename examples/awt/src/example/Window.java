package example;

import java.awt.Frame;
import java.awt.Label;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class Window {
  public static void main(String[] args) {
    Frame frame = new Frame("javacosmofy AWT test");
    frame.add(new Label("java.desktop was selected from the module repository."));
    frame.setSize(460, 120);
    frame.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent event) { System.exit(0); }
    });
    frame.setVisible(true);
  }
}
