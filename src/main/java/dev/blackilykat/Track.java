package dev.blackilykat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class Track implements Serializable {
    public File file;
    public long checksum;
    public long lastModified;

    public Track(File file) throws IOException {
        this.file = file;
        CheckedInputStream inputStream = new CheckedInputStream(new FileInputStream(file), new CRC32());
        // 1MB
        byte[] buffer = new byte[1048576];
        while(inputStream.read(buffer, 0, buffer.length) >= 0) {
        }
        this.checksum = inputStream.getChecksum().getValue();

        this.lastModified = file.lastModified();
    }
}
