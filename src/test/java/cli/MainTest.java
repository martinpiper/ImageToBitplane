package cli;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class MainTest {
    @Test
    public void main1_oldbridge() throws Exception {
        String currentDir = System.getProperty("user.dir");
        System.out.println("Current dir using System:" + currentDir);

        String args[] = {
            "--splitmaps" ,
            "--chars" ,
            "--rgbshift" ,
            "4" ,
            "4" ,
            "4" ,
            "--rgbfactor" ,
            "255" ,
            "196" ,
            "112" ,
            "10" ,
            "--rgbfactor" ,
            "255" ,
            "255" ,
            "214" ,
            "50" ,
            "--rgbfactor" ,
            "236" ,
            "98" ,
            "96" ,
            "50" ,
            "--newpalettes" ,
            "--forcergb" ,
            "0" ,
            "0" ,
            "0" ,
            "--paletteoffset" ,
            "0" ,
            "--palettesize" ,
            "16" ,
            "--startxy" ,
            "0" ,
            "0" ,
            "--image" ,
            "src/test/resources/oldbridge cropped_chars 512.bmp" ,
            "--tilewh" ,
            "8" ,
            "8" ,
            "--imagequantize" ,
            "16" ,
            "--nowritepass" ,
            "--palettequantize" ,
            "16" ,
            "--image" ,
            "src/test/resources/oldbridge cropped_chars 1024.bmp" ,
            "--tilewh" ,
            "8" ,
            "8" ,
            "--fitpalettes" ,
            "--outputplanes" ,
            "target/chars1024_plane" ,
            "--outputscrcol" ,
            "target/chars1024_scr.bin" ,
            "--outputpalettes" ,
            "target/chars1024_paletteData.bin" ,
            "--nostacking" ,
            "--numbitplanes" ,
            "4" ,
            "--convertwritepass"
        };

        Main.main(args);

        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_paletteData.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_paletteData.bin")));

        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_plane0.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_plane0.bin")));
        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_plane1.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_plane1.bin")));
        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_plane2.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_plane2.bin")));
        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_plane3.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_plane3.bin")));

        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_scr.bin")) , Files.readAllBytes(Paths.get("expected/chars1024_scr.bin")));
        assertArrayEquals(Files.readAllBytes(Paths.get("target/chars1024_scr.bin2")) , Files.readAllBytes(Paths.get("expected/chars1024_scr.bin2")));
    }
}