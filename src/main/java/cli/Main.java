package cli;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) throws Exception {
        String path = "src/test/resources/TestImage1.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\town_rpg_pack\\town_rpg_pack\\graphics\\tiles-map.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\dirt-tiles.png";
        BufferedImage img = ImageIO.read(new File(path));
        int imageWidth = img.getWidth();
        int imageHeight = img.getHeight();
        Color[] imageColours = new Color[imageWidth * imageHeight];
        for (int y = 0 ; y < imageHeight ; y++) {
            for (int x = 0; x < imageWidth; x++) {
                imageColours[x+(y*imageWidth)] = new Color(img.getRGB(x,y));
            }
        }


        ArrayList<HashMap<Color,Integer>> palettes = new ArrayList<>();

        int paletteMaxLen = 8;
        HashMap<Color , Integer> forcedColourIndex = new HashMap<>();
        forcedColourIndex.put(new Color(255, 0, 255) , forcedColourIndex.size());

        int tileWidth = 16 , tileHeight = 16;
        int startX = 0 , startY = 0;

        int numBitplanes = 3;
        ByteBuffer[] bitplaneData = new ByteBuffer[numBitplanes];
        for (int bp = 0 ; bp < numBitplanes ; bp++) {
            bitplaneData[bp] = ByteBuffer.allocate((2*imageWidth * imageHeight)/8);
        }

        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; ) {
                // First collect all unique colours used in the tile
                HashSet<Color> usedColours = new HashSet<>();
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Color colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            usedColours.add(colour);
                        }
                    }
                }

                // Find the best fit existing palette index
                int bestFoundPalette = -1;
                int bestFoundNum = -1;
                int currentPalette = 0;
                for (HashMap<Color,Integer> palette : palettes) {
                    int numColoursMatching = 0;
                    int numColoursMissing = usedColours.size();
                    for (Color colour : usedColours) {
                        if (palette.containsKey(colour)) {
                            numColoursMatching++;
                            numColoursMissing--;
                        }
                    }

                    // If all the colours are found in a palette, then early out
                    if (numColoursMatching == usedColours.size()) {
                        bestFoundNum = numColoursMatching;
                        bestFoundPalette = currentPalette;
                        break;
                    }

                    // Choose the best palette to extend if possible, greedy fill
                    if (numColoursMatching > bestFoundNum) {
                        // If there is room to extend the existing palette
                        if (numColoursMissing < (paletteMaxLen - palette.size())) {
                            bestFoundNum = numColoursMatching;
                            bestFoundPalette = currentPalette;
                            // Continue searching...
                        }
                    }
                    currentPalette++;
                }

                HashMap<Color,Integer> palette;
                if (bestFoundPalette < 0) {
                    palette = (HashMap<Color, Integer>) forcedColourIndex.clone();
                    palettes.add(palette);
                } else {
                    palette = palettes.get(bestFoundPalette);
                }

                // Update any new colours into the best palette
                for (Color colour : usedColours) {
                    if (palette.size() >= paletteMaxLen) {
                        break;
                    }
                    if (!palette.containsKey(colour)) {
                        palette.put(colour, palette.size());
                    }
                }

                assert palette.size() < paletteMaxLen;

                // Now convert the tile to bitplane data based on the colours in the palette and the index values
                byte[] theTile = new byte[tileWidth * tileHeight];
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        // If there is no colour then assume it's the first palette entry, which should be transparent
                        theTile[tx + (ty * tileWidth)] = 0;

                        // Then try to map the colour if it exists
                        Color colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            Integer value = palette.get(colour);
                            if (value != null) {
                                theTile[tx + (ty * tileWidth)] = value.byteValue();
                                // Remove the pixel, since it has been processed
                                imageColours[x + tx + ((y + ty) * imageWidth)] = null;
                            }
                        }
                    }
                }
                // The pattern of pixels to pull from the linear row/column tile data into the bitplane data
                int[] indexPick = {
                        0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
                        0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
                        0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27,
                        0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,
                        0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,
                        0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,
                        0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x67,
                        0x70,0x71,0x72,0x73,0x74,0x75,0x76,0x77,

                        0x08,0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,
                        0x18,0x19,0x1a,0x1b,0x1c,0x1d,0x1e,0x1f,
                        0x28,0x29,0x2a,0x2b,0x2c,0x2d,0x2e,0x2f,
                        0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x3e,0x3f,
                        0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,
                        0x58,0x59,0x5a,0x5b,0x5c,0x5d,0x5e,0x5f,
                        0x68,0x69,0x6a,0x6b,0x6c,0x6d,0x6e,0x6f,
                        0x78,0x79,0x7a,0x7b,0x7c,0x7d,0x7e,0x7f,

                        0x80,0x81,0x82,0x83,0x84,0x85,0x86,0x87,
                        0x90,0x91,0x92,0x93,0x94,0x95,0x96,0x97,
                        0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,
                        0xb0,0xb1,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,
                        0xc0,0xc1,0xc2,0xc3,0xc4,0xc5,0xc6,0xc7,
                        0xd0,0xd1,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,
                        0xe0,0xe1,0xe2,0xe3,0xe4,0xe5,0xe6,0xe7,
                        0xf0,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,

                        0x88,0x89,0x8a,0x8b,0x8c,0x8d,0x8e,0x8f,
                        0x98,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,
                        0xa8,0xa9,0xaa,0xab,0xac,0xad,0xae,0xaf,
                        0xb8,0xb9,0xba,0xbb,0xbc,0xbd,0xbe,0xbf,
                        0xc8,0xc9,0xca,0xcb,0xcc,0xcd,0xce,0xcf,
                        0xd8,0xd9,0xda,0xdb,0xdc,0xdd,0xde,0xdf,
                        0xe8,0xe9,0xea,0xeb,0xec,0xed,0xee,0xef,
                        0xf8,0xf9,0xfa,0xfb,0xfc,0xfd,0xfe,0xff
                };
                byte[][] bitplaneDataTemp = new byte[numBitplanes][(indexPick.length)/8];

                for (int bp = 0 ; bp < numBitplanes ; bp++) {
                    int shiftedPixels = 0;
                    int shiftedPixelsCount = 0;
                    int pi = 0;
                    for (int index = 0 ; index < indexPick.length ; index++) {
                        shiftedPixels = shiftedPixels << 1;
                        if ((theTile[indexPick[index]] & (1<<bp)) > 0) {
                            shiftedPixels = shiftedPixels | 1;
                        }
                        shiftedPixelsCount++;

                        if (shiftedPixelsCount == 8) {
                            bitplaneDataTemp[bp][pi]=(byte) shiftedPixels;
                            pi++;
                            shiftedPixels = 0;
                            shiftedPixelsCount = 0;
                        }
                    }
                }

                for (int bp = 0 ; bp < numBitplanes ; bp++) {
                    bitplaneData[bp].put(bitplaneDataTemp[bp]);
                }

                    // Now calculate if there is any data left
                usedColours.clear();
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Color colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            usedColours.add(colour);
                        }
                    }
                }
                if (usedColours.size() > 1) {
                    System.out.println("Stacked x=" + x + " y=" + y);
                    continue;
                }
                x+=tileWidth;
            }
        }

        for (int bp = 0 ; bp < numBitplanes ; bp++) {
            FileChannel fc = new FileOutputStream("target/plane"+bp+".bin").getChannel();
            bitplaneData[bp].flip();
            fc.write(bitplaneData[bp]);
            fc.close();
        }

        System.out.println("num palettes=" + palettes.size());
        for (HashMap<Color,Integer> palette : palettes) {
            System.out.println("palette size=" + palette.size());
        }
        System.out.println("Foo");
    }
}
