package cli;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class Main {

    static int colourShiftRed = 0 , colourShiftGreen = 0 , colourShiftBlue = 0;
    public static Color ApplyColorLimitsFromColour(Color colour) {
        Color newColour = new Color((colour.getRed()>>colourShiftRed)<<colourShiftRed , (colour.getGreen()>>colourShiftGreen)<<colourShiftGreen , (colour.getBlue()>>colourShiftBlue)<<colourShiftBlue );
        return newColour;
    }

    public static int ParseValueFrom(String value) {
        if (value.startsWith("0x")) {
            return Integer.parseInt(value.substring(2) , 16);
        } else if (value.startsWith("$")) {
            return Integer.parseInt(value.substring(1) , 16);
        }
        return Integer.parseInt(value);
    }

    static int paletteOffset = 5;
    static int paletteMaxLen = 8;
    static int spriteXPos = 0;
    static int spriteYPos = 0xd0;
    static int paletteMaxQuantize = 32;
    static ArrayList<HashMap<Integer,Integer>> palettes = new ArrayList<>();
    static HashMap<Integer , Integer> forcedColourIndex = new HashMap<>();
    static int tileWidth = 16 , tileHeight = 16;
    static int startX = 0 , startY = 0;
    static BufferedImage img = null;
    static int imageWidth = 0;
    static int imageHeight = 0;
    static Integer[] imageColours = null;
    static int numBitplanes = 3;
    static ByteBuffer[] bitplaneData = null;
    static byte currentTile = 0;
    static ByteBuffer screenTileData = null;
    static ByteBuffer screenColourData = null;
    static String outputPlanes = null;
    static String outputScreenData = null;
    static PrintStream outputSprites = null;
    static String outputPalettes = null;
    static boolean useStacking = false;

    public static void main(String[] args) throws Exception {

        for (int i = 0 ; i < args.length ; i++) {
            if (args[i].compareToIgnoreCase("--rgbshift") == 0) {
                colourShiftRed = ParseValueFrom(args[i+1]);
                colourShiftGreen = ParseValueFrom(args[i+2]);
                colourShiftBlue = ParseValueFrom(args[i+3]);
                i+=3;
                continue;
            } else if (args[i].compareToIgnoreCase("--newpalettes") == 0) {
                palettes = new ArrayList<>();
                continue;
            } else if (args[i].compareToIgnoreCase("--resetforcergb") == 0) {
                forcedColourIndex.clear();
                continue;
            } else if (args[i].compareToIgnoreCase("--forcergb") == 0) {
                forcedColourIndex.put(ApplyColorLimitsFromColour(new Color(ParseValueFrom(args[i+1]), ParseValueFrom(args[i+2]), ParseValueFrom(args[i+3]))).getRGB() , forcedColourIndex.size());
//        forcedColourIndex.put(ApplyColorLimitsFromColour(new Color(164, 218, 244)).getRGB() , forcedColourIndex.size());
//        forcedColourIndex.put(ApplyColorLimitsFromColour(new Color(119, 122, 133)).getRGB() , forcedColourIndex.size());
//        forcedColourIndex.put(ApplyColorLimitsFromColour(new Color(0, 0, 0)).getRGB() , forcedColourIndex.size());
                i+=3;
                continue;
            } else if (args[i].compareToIgnoreCase("--paletteoffset") == 0) {
                paletteOffset = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--palettesize") == 0) {
                paletteMaxLen = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--spritexy") == 0) {
                spriteXPos = ParseValueFrom(args[i+1]);
                spriteYPos = ParseValueFrom(args[i+2]);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--imagequantize") == 0) {
                paletteMaxQuantize = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--tilewh") == 0) {
                tileWidth = ParseValueFrom(args[i+1]);
                tileHeight = ParseValueFrom(args[i+2]);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--startxy") == 0) {
                startX = ParseValueFrom(args[i+1]);
                startY = ParseValueFrom(args[i+2]);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--image") == 0) {
                img = ImageIO.read(new File(args[i+1]));
                i++;

                imageWidth = img.getWidth();
                imageHeight = img.getHeight();
                imageColours = new Integer[imageWidth * imageHeight];
                // Applies target platform colour limits first
                for (int y = 0 ; y < imageHeight ; y++) {
                    for (int x = 0; x < imageWidth; x++) {
                        Color colour = new Color(img.getRGB(x,y));
                        Color newColour = ApplyColorLimitsFromColour(colour);
                        imageColours[x+(y*imageWidth)] = newColour.getRGB();
                    }
                }
                ImageQuantize();
                continue;
            } else if (args[i].compareToIgnoreCase("--numbitplanes") == 0) {
                numBitplanes = ParseValueFrom(args[i+1]);
                i++;

                bitplaneData = new ByteBuffer[numBitplanes];
                for (int bp = 0 ; bp < numBitplanes ; bp++) {
                    bitplaneData[bp] = ByteBuffer.allocate(8192); // MPi: TODO: Make 8192 configurable
                }

                currentTile = 0;
                continue;
            } else if (args[i].compareToIgnoreCase("--nowrite") == 0) {
                outputScreenData = null;
                outputSprites = null;
                outputPlanes = null;
                outputPalettes = null;
                continue;
            } else if (args[i].compareToIgnoreCase("--nowritepass") == 0) {
                outputScreenData = null;
                outputSprites = null;
                outputPlanes = null;
                outputPalettes = null;
                TileConvert();
                continue;
            } else if (args[i].compareToIgnoreCase("--outputplanes") == 0) {
                outputPlanes = args[i+1];
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputscrcol") == 0) {
                outputScreenData = args[i+1];
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputsprites") == 0) {
                outputSprites = new PrintStream(new FileOutputStream(args[i+1]));
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputpalettes") == 0) {
                outputPalettes = args[i+1];
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--convertwritepass") == 0) {
                TileConvert();
                OutputFiles();
                continue;
            } else if (args[i].compareToIgnoreCase("--convertpass") == 0) {
                TileConvert();
                continue;
            } else if (args[i].compareToIgnoreCase("--usestacking") == 0) {
                useStacking = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--nostacking") == 0) {
                useStacking = false;
                continue;
            }
        }


//        String inputPath = "src/test/resources/TestImage1.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\town_rpg_pack\\town_rpg_pack\\graphics\\tiles-map.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\dirt-tiles.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\oldbridge.gif";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\oldbridge cropped.bmp";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\map_9 - Copy.png";
        return;
    }

    private static void OutputFiles() throws IOException {
        FileChannel fc;
        for (int bp = 0 ; bp < numBitplanes ; bp++) {
            if (outputScreenData != null || outputSprites != null) {
                fc = new FileOutputStream(outputPlanes + bp + ".bin").getChannel();
                bitplaneData[bp].flip();
                fc.write(bitplaneData[bp]);
                fc.close();
            }
        }

        if (outputScreenData != null) {
            fc = new FileOutputStream(outputScreenData).getChannel();
            screenTileData.flip();
            fc.write(screenTileData);
            screenColourData.flip();
            fc.write(screenColourData);
            fc.close();
        }

        if (outputPalettes != null) {
            fc = new FileOutputStream(outputPalettes).getChannel();
            System.out.println("num palettes=" + palettes.size());
            int outNum = 0;
            for (HashMap<Integer, Integer> palette : palettes) {
                System.out.println("palette size=" + palette.size());
                if (outNum < 16) {
                    byte[] thisPalette = new byte[paletteMaxLen * 2];
                    for (Map.Entry<Integer, Integer> entry : palette.entrySet()) {
                        Color colour = new Color(entry.getKey());
                        thisPalette[(entry.getValue() * 2)] = (byte) ((colour.getGreen() >> 4) << 4);
                        thisPalette[(entry.getValue() * 2)] |= (byte) (colour.getRed() >> 4);
                        thisPalette[(entry.getValue() * 2) + 1] = (byte) (colour.getBlue() >> 4);
                    }
                    fc.write(ByteBuffer.wrap(thisPalette));
                }
                outNum++;
            }
            fc.close();
        }
    }

    private static void TileConvert() {
        System.out.println("To tiles...");
        screenTileData = ByteBuffer.allocate((imageWidth * imageHeight)/tileWidth/tileHeight);
        screenColourData = ByteBuffer.allocate((imageWidth * imageHeight)/tileWidth/tileHeight);

        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; ) {
                System.out.println(";Process x=" + x + " y=" + y);
                if (outputSprites != null) {
                    outputSprites.println(";Process x=" + x + " y=" + y);
                }
                // First collect all unique colours used in the tile
                SortedSet<Integer> usedColours = new TreeSet<>();
                usedColours.addAll(forcedColourIndex.keySet());
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    if (usedColours.size() >= paletteMaxLen) {
                        break;
                    }
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        if (usedColours.size() >= paletteMaxLen) {
                            break;
                        }
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            usedColours.add(colour);
                        }
                    }
                }

                // Find the best fit existing palette index
                HashMap<Integer,Integer> bestFoundPalette = null;
                byte bestFoundPaletteIndex = 0;
                int bestFoundNum = -1;
                byte currentPaletteIndex = 0;
                for (HashMap<Integer,Integer> palette : palettes) {
                    int numColoursMatching = 0;
                    int numColoursMissing = usedColours.size();
                    boolean rejectPalette = false;
                    for (Integer colour : usedColours) {
                        if (palette.containsKey(colour)) {
                            Integer colourIndex = palette.get(colour);
                            if (forcedColourIndex.containsKey(colour)) {
                                if (!forcedColourIndex.get(colour).equals(colourIndex)) {
                                    // Reject the colour choice if the colour is in the forced colour table and the colour doesn't match the index
                                    rejectPalette = true;
                                    continue;
                                }
                            }
                            numColoursMatching++;
                            numColoursMissing--;
                        }
                    }
                    if (rejectPalette) {
                        currentPaletteIndex++;
                        continue;
                    }

                    // If all the colours are found in a palette, then early out
                    if (numColoursMatching == usedColours.size() || numColoursMatching >= palette.size()) {
                        bestFoundNum = numColoursMatching;
                        bestFoundPalette = palette;
                        bestFoundPaletteIndex = currentPaletteIndex;
                        break;
                    }

                    // Choose the best palette to extend if possible, greedy fill
                    if (numColoursMatching > bestFoundNum) {
                        // If there is room to extend the existing palette
                        // The "numColoursMatching > numColoursMissing" will favour palette reuse and sprite stacking instead of creating new palettes
                        if (useStacking) {
                            if (numColoursMatching > numColoursMissing || numColoursMissing <= (paletteMaxLen - palette.size())) {
                                bestFoundNum = numColoursMatching;
                                bestFoundPalette = palette;
                                bestFoundPaletteIndex = currentPaletteIndex;
                                // Continue searching...
                            }
                        } else {
                            if (numColoursMissing <= (paletteMaxLen - palette.size())) {
                                bestFoundNum = numColoursMatching;
                                bestFoundPalette = palette;
                                bestFoundPaletteIndex = currentPaletteIndex;
                                // Continue searching...
                            }
                        }
                    }

                    currentPaletteIndex++;
                }

                HashMap<Integer,Integer> palette;
                if (bestFoundPalette == null) {
                    bestFoundPaletteIndex = (byte)palettes.size();
                    palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    palettes.add(palette);
                } else {
                    palette = bestFoundPalette;
                }

                // Update any new colours into the best palette
                for (Integer colour : usedColours) {
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
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
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

                boolean tileHasData = false;
                if (outputScreenData != null || outputSprites != null) {
                    for (int bp = 0; bp < numBitplanes; bp++) {
                        int shiftedPixels = 0;
                        int shiftedPixelsCount = 0;
                        int pi = 0;
                        for (int index = 0; index < indexPick.length; index++) {
                            shiftedPixels = shiftedPixels << 1;
                            if ((theTile[indexPick[index]] & (1 << bp)) > 0) {
                                shiftedPixels = shiftedPixels | 1;
                                tileHasData = true;
                            }
                            shiftedPixelsCount++;

                            if (shiftedPixelsCount == 8) {
                                bitplaneDataTemp[bp][pi] = (byte) shiftedPixels;
                                pi++;
                                shiftedPixels = 0;
                                shiftedPixelsCount = 0;
                            }
                        }
                    }
                }

                if (outputScreenData != null) {
                    screenTileData.put(currentTile);
                    screenColourData.put((byte) (bestFoundPaletteIndex & 0xf));
                } else if (outputSprites != null) {
                    if (tileHasData) {
                        if (currentTile >= 24) {
                            outputSprites.print(";");
                        }
                        outputSprites.println("b" + currentTile + ",b" + (bestFoundPaletteIndex + paletteOffset) + ",b" + spriteYPos + ",b" + spriteXPos);
                    } else {
                        outputSprites.println(";Empty");
                    }
                }

                if ((outputScreenData != null) || ((outputSprites != null) && tileHasData)) {
                    // Advances the tile number, could do with duplicate check here
                    for (int bp = 0; bp < numBitplanes; bp++) {
                        bitplaneData[bp].put(bitplaneDataTemp[bp]);
                    }
                    currentTile++;
                }

                // Now calculate if there is any data left
                usedColours.clear();
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            usedColours.add(colour);
                        }
                    }
                }
                if (useStacking && usedColours.size() > 0) {
                    System.out.println(";Stacked x=" + x + " y=" + y);
                    if (outputSprites != null) {
                        outputSprites.println(";Stacked x=" + x + " y=" + y);
                    }
                    continue;
                }
                x+=tileWidth;

                if (outputSprites != null) {
                    spriteXPos += tileWidth;
                    if (spriteXPos >= 256) {
                        spriteXPos = 0;
                        spriteYPos -= tileHeight;
                    }
                }
            }
            if (outputSprites != null) {
                spriteXPos = 0;
                spriteYPos -= tileHeight;
            }
        }

        if (outputSprites != null) {
            outputSprites.flush();
        }

        System.out.println("num palettes=" + palettes.size());
        for (HashMap<Integer, Integer> palette : palettes) {
            System.out.println("palette size=" + palette.size());
        }
    }

    private static void ImageQuantize() {
        System.out.println("Quantize...");

        // Quantize tiles down to a maximum number of colours
        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; x+= tileWidth) {
                HashMap<Integer,Integer> usedColours = new HashMap<>();
                for (Integer colour : forcedColourIndex.keySet()) {
                    usedColours.put(colour , tileWidth*tileHeight);
                }
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            if (!usedColours.containsKey(colour)) {
                                usedColours.put(colour, 0);
                            } else {
                                Integer num = usedColours.get(colour);
                                usedColours.put(colour, num+1);
                            }
                        }
                    }
                }
                // Reduce until it fits
                while (usedColours.size() > paletteMaxQuantize) {
                    System.out.println("Reduce x=" + x + " y=" + y);
                    // Find the least used colour
                    Integer chosenMinColour = null;
                    int count = 0;
                    for (Map.Entry<Integer,Integer> entry: usedColours.entrySet()) {
                        if (chosenMinColour == null || entry.getValue() < count) {
                            chosenMinColour = entry.getKey();
                            count = entry.getValue();
                        }
                    }

                    Color source = new Color(chosenMinColour);
                    Integer closestColourToMin = null;
                    double difference = -1;
                    for (Map.Entry<Integer,Integer> entry: usedColours.entrySet()) {
                        // Skip the same colour
                        if (chosenMinColour.equals(entry.getKey())) {
                            continue;
                        }

                        Color destination = new Color(entry.getKey());
                        double thisDifference = (source.getRed() - destination.getRed()) * (source.getRed() - destination.getRed());
                        thisDifference += (source.getGreen() - destination.getGreen()) * (source.getGreen() - destination.getGreen());
                        thisDifference += (source.getBlue() - destination.getBlue()) * (source.getBlue() - destination.getBlue());

                        if (closestColourToMin == null || thisDifference < difference) {
                            closestColourToMin = entry.getKey();
                            difference = thisDifference;
                        }
                    }

                    // Replace colours in the tile
                    for (int ty = 0 ; ty < tileHeight ; ty++) {
                        for (int tx = 0 ; tx < tileWidth ; tx++) {
                            Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                            if (colour != null) {
                                if (colour.equals(chosenMinColour)) {
                                    imageColours[x + tx + ((y + ty) * imageWidth)] = closestColourToMin;
                                }
                            }
                        }
                    }

                    // Update the used totals
                    usedColours.put(closestColourToMin , usedColours.get(closestColourToMin) + usedColours.get(chosenMinColour));
                    usedColours.remove(chosenMinColour);
                }
            }
        }
    }
}
