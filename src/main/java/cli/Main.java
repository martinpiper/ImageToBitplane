package cli;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

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

    static boolean useSquaredModel = true;
    static int paletteOffset = 5;
    static int paletteMaxLen = 8;
    static int spriteXPos = 0;
    static int spriteYPos = 0xd0;
    static int paletteMaxQuantize = 32;
    static ArrayList<HashMap<Integer,Integer>> palettes = new ArrayList<>();
    static HashMap<Integer , Integer> forcedColourIndex = new HashMap<>();
    static HashMap<Integer , Double> factorColourIndex = new HashMap<>();
    static int tileWidth = 16 , tileHeight = 16;
    static int startX = 0 , startY = 0;
    static BufferedImage img = null;
    static int imageWidth = 0;
    static int imageHeight = 0;
    static Integer[] imageColours = null;
    static Integer[] imageColoursOriginal = null;
    static int numBitplanes = 3;
    static ByteBuffer[] bitplaneData = null;
    static ByteBuffer tileByteData = null;
    static int currentTile = 0;
    static ByteBuffer screenTileData = null;
    static ByteBuffer screenColourData = null;
    static String outputPlanes = null;
    static String outputTileBytes = null;
    static String outputScreenData = null;
    static PrintStream outputSprites = null;
    static PrintStream outputSprites2 = null;
    static String outputPalettes = null;
    static boolean useStacking = false;
    static boolean fitPalettes = false;
    static TreeMap<String , TileIndexFlip> tileToIndexFlip = new TreeMap<String , TileIndexFlip>();

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
                i+=3;
                continue;
            } else if (args[i].compareToIgnoreCase("--rgbfactor") == 0) {
                double factor = Double.parseDouble(args[i+4]);
                factorColourIndex.put(ApplyColorLimitsFromColour(new Color(ParseValueFrom(args[i+1]), ParseValueFrom(args[i+2]), ParseValueFrom(args[i+3]))).getRGB() , factor);
                i+=4;
                continue;
            } else if (args[i].compareToIgnoreCase("--resetrgbfactor") == 0) {
                factorColourIndex.clear();
                continue;
            } else if (args[i].compareToIgnoreCase("--paletteoffset") == 0) {
                paletteOffset = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--palettesize") == 0) {
                paletteMaxLen = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--loadpalette") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                if (bytes.length / 2 <= paletteMaxLen) {
                    HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                    // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    for (int j = 0; j < bytes.length; j += 2) {
                        int red = (bytes[j] & 0xf) << colourShiftRed;
                        int green = ((bytes[j] >> 4) & 0xf) << colourShiftRed;
                        int blue = (bytes[j + 1] & 0xf) << colourShiftBlue;

                        int rgb = ApplyColorLimitsFromColour(new Color(red, green, blue)).getRGB();
                        if (!palette.containsKey(rgb)) {
                            palette.put(rgb, j / 2);
                        }
                    }
                    palettes.add(palette);
                } else {
                    HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                    // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    for (int j = 0; j < bytes.length; j += 2) {
                        int red = (bytes[j] & 0xf) << colourShiftRed;
                        int green = ((bytes[j] >> 4) & 0xf) << colourShiftRed;
                        int blue = (bytes[j + 1] & 0xf) << colourShiftBlue;

                        int rgb = ApplyColorLimitsFromColour(new Color(red, green, blue)).getRGB();
                        if (!palette.containsKey(rgb)) {
                            palette.put(rgb, palette.size());
                        }
                        if (palette.size() >= paletteMaxLen) {
                            palettes.add(palette);
                            palette = new HashMap<Integer, Integer>();
                            // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                        }
                    }
                    if (!palette.isEmpty()) {
                        palettes.add(palette);
                    }
                }

                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--loadpalettebestfit") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                for (int j = 0; j < bytes.length; j += 2) {
                    int red = (bytes[j] & 0xf) << colourShiftRed;
                    int green = ((bytes[j] >> 4) & 0xf) << colourShiftRed;
                    int blue = (bytes[j + 1] & 0xf) << colourShiftBlue;

                    int rgb = ApplyColorLimitsFromColour(new Color(red, green, blue)).getRGB();

                    boolean found = false;
                    for (HashMap<Integer, Integer> palette : palettes) {
                        if (palette.containsKey(rgb)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        for (HashMap<Integer, Integer> palette : palettes) {
                            if (palette.size() < paletteMaxLen) {
                                palette.put(rgb, palette.size());
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        HashMap<Integer, Integer> palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                        palette.put(rgb, palette.size());
                        palettes.add(palette);
                    }
                }

                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--spritexy") == 0) {
                spriteXPos = ParseValueFrom(args[i+1]);
                spriteYPos = ParseValueFrom(args[i+2]);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--imagequantize") == 0) {
                paletteMaxQuantize = ParseValueFrom(args[i+1]);
                ImageQuantize();
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
                imageColoursOriginal = imageColours.clone();
                tileWidth = imageWidth;
                tileHeight = imageHeight;
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
                outputSprites2 = null;
                outputPlanes = null;
                outputPalettes = null;
                outputTileBytes = null;
                currentTile = 0;
                continue;
            } else if (args[i].compareToIgnoreCase("--nowritepass") == 0) {
                outputScreenData = null;
                outputSprites = null;
                outputSprites2 = null;
                outputPlanes = null;
                outputPalettes = null;
                outputTileBytes = null;
                currentTile = 0;
                TileConvert();
                continue;
            } else if (args[i].compareToIgnoreCase("--outputplanes") == 0) {
                outputPlanes = args[i+1];
                outputTileBytes = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputtilebytes") == 0) {
                outputTileBytes = args[i+1];
                outputPlanes = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputscrcol") == 0) {
                outputScreenData = args[i+1];
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputsprites") == 0) {
                outputSprites = new PrintStream(new FileOutputStream(args[i+1]));
                outputSprites2 = new PrintStream(new FileOutputStream(args[i+1] + ".a"));
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
            } else if (args[i].compareToIgnoreCase("--palettequantize") == 0) {
                int targetPalettes = ParseValueFrom(args[i+1]);
                i++;
                PaletteQuantize(targetPalettes);

                continue;
            } else if (args[i].compareToIgnoreCase("--fitpalettes") == 0) {
                fitPalettes = true;
                continue;
            }

            System.err.println("Unknown option: " + args[i]);
        }

//        String inputPath = "src/test/resources/TestImage1.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\town_rpg_pack\\town_rpg_pack\\graphics\\tiles-map.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\dirt-tiles.png";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\oldbridge.gif";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\oldbridge cropped.bmp";
//        String path = "C:\\Users\\Martin Piper\\Downloads\\map_9 - Copy.png";
        return;
    }

    private static void PaletteQuantize(int targetPalettes) {
        while (palettes.size() > targetPalettes) {
            System.out.println("Palette quantize pass. Size = " + palettes.size());
            //    * Find the palette with the smallest number of image pixels
            HashMap<Integer,Integer> smallestPalette = null;
            double smallestNumPixels = 0;
            for (HashMap<Integer,Integer> palette : palettes) {
                double numPixels = 0;
                for (int y = 0 ; y < imageHeight ; y++) {
                    for (int x = 0; x < imageWidth; x++) {
                        int theColour = imageColoursOriginal[x+(y*imageWidth)];
                        if (palette.containsKey(theColour)) {
                            numPixels += factorColourIndex.getOrDefault(theColour, 1.0);
                        }
                    }
                }
                if (smallestPalette == null || numPixels < smallestNumPixels) {
                    smallestNumPixels = numPixels;
                    smallestPalette = palette;
                }
            }

            assert smallestPalette != null;

            //    * Find the next closest colour palette

            HashMap<Integer,Integer> bestPalette = null;
            double bestColourDifference = 0;

            for (HashMap<Integer,Integer> palette : palettes) {
                if (palette == smallestPalette) {
                    continue;
                }
                double colourDifference = getPaletteDifference(smallestPalette, palette);

                if (bestPalette == null || colourDifference < bestColourDifference) {
                    bestColourDifference = colourDifference;
                    bestPalette = palette;
                }
            }

            assert bestPalette != null;

            //    * If free slots, merge in most used colours from the smallest palette
            while (bestPalette.size() < paletteMaxLen) {

                // Find remaining colours not in bestPalette from smallestPalette
                HashSet<Integer> missingColours = new HashSet<>();
                for (int aColour : smallestPalette.keySet()) {
                    if (!bestPalette.containsKey(aColour)) {
                        missingColours.add(aColour);
                    }
                }

                if (missingColours.isEmpty()) {
                    break;
                }

                // Get the most used missing colour
                double bestLargestCount = 0;
                int bestColour = 0;

                for (int aColour : missingColours) {
                    double numPixels = 0;
                    for (int y = 0; y < imageHeight; y++) {
                        for (int x = 0; x < imageWidth; x++) {
                            int theColour = imageColoursOriginal[x + (y * imageWidth)];
                            if (theColour == aColour) {
                                numPixels += factorColourIndex.getOrDefault(theColour, 1.0);
                            }
                        }
                    }
                    if (numPixels > bestLargestCount) {
                        bestColour = aColour;
                        bestLargestCount = numPixels;
                    }
                }

                bestPalette.put(bestColour, bestPalette.size());
            }
            //    * Delete the smallest palette
            palettes.remove(smallestPalette);

            //    * Repeat until the target palette number is reached
        }

        Color[][] visualisePalettes = new Color[palettes.size()][paletteMaxLen];
        for (int i = 0 ; i < palettes.size() ; i++) {
            for (Map.Entry<Integer,Integer> entry : palettes.get(i).entrySet()) {
                visualisePalettes[i][entry.getValue()] = new Color(entry.getKey());
            }
        }

        System.out.print("Finished palette quantize");
    }

    private static double getPaletteDifference(HashMap<Integer, Integer> smallestPalette, HashMap<Integer, Integer> palette) {
        return getColoursDifference(smallestPalette.keySet() , palette.keySet());
    }

    private static double getColoursDifference(Set<Integer> smallestPalette, Set<Integer> palette) {
        double colourDifference = 0;
        for (int sourceColour : smallestPalette) {
            Color theSourceColour = new Color(sourceColour);
            for (int aColour : palette) {
                Color theColour = new Color(aColour);
                double thisDifference = getColourDifference(theSourceColour, theColour);
                colourDifference += thisDifference;
            }
        }
        return colourDifference;
    }

    private static void OutputFiles() throws IOException {
        FileChannel fc;
        if (outputTileBytes != null) {
            tileByteData.flip();
            if (tileByteData.limit() >= 8192) {
                ByteBuffer bank1 = tileByteData.slice();
                ByteBuffer bank2 = tileByteData.slice();

                bank1.position(0);
                bank1.limit(8192);
                bank2.position(8192);

                fc = new FileOutputStream(outputTileBytes).getChannel();
                fc.write(bank1);
                fc.close();

                fc = new FileOutputStream(outputTileBytes + "2").getChannel();
                fc.write(bank2);
                fc.close();

            } else {
                fc = new FileOutputStream(outputTileBytes).getChannel();
                fc.write(tileByteData);
                fc.close();
            }
        } else {
            for (int bp = 0; bp < numBitplanes; bp++) {
                if (outputScreenData != null || outputSprites != null) {
                    fc = new FileOutputStream(outputPlanes + bp + ".bin").getChannel();
                    bitplaneData[bp].flip();
                    fc.write(bitplaneData[bp]);
                    fc.close();
                }
            }
        }

        if (outputScreenData != null) {
            fc = new FileOutputStream(outputScreenData).getChannel();
            screenTileData.flip();
            fc.write(screenTileData);
            if (outputTileBytes == null) {
                screenColourData.flip();
                fc.write(screenColourData);
            }
            fc.close();
        }

        if (outputPalettes != null) {
            fc = new FileOutputStream(outputPalettes).getChannel();
            System.out.println("num palettes=" + palettes.size());
            int outNum = 0;
            for (HashMap<Integer, Integer> palette : palettes) {
                System.out.println("palette size=" + palette.size());
                if (outNum < 32) {
                    int maxEntryIndex = 0;
                    if (palettes.size() == 1) {
                        for (Map.Entry<Integer, Integer> entry : palette.entrySet()) {
                            if ((entry.getValue()+1) > maxEntryIndex) {
                                maxEntryIndex = entry.getValue() + 1;
                            }
                        }
                    } else {
                        maxEntryIndex = paletteMaxLen;
                    }
                    byte[] thisPalette = new byte[maxEntryIndex * 2];
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
        tileByteData = ByteBuffer.allocate(tileWidth*tileHeight*1024);  // Ample space even for extended character data

        boolean newSprite = true;

        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; ) {
                System.out.println(";Process x=" + x + " y=" + y);
                if (outputSprites != null) {
                    outputSprites.println(";Process x=" + x + " y=" + y);
                    if (newSprite) {
                        outputSprites2.println("EmitSpriteFrame"+ x +"_" + y);
                        outputSprites2.println("\t+MEmitSpriteFrame_Preserve");
                        newSprite = false;
                    }
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
                HashMap<Integer, Integer> resultPalette = null;
                int bestFoundPaletteIndex = 0;
                if (fitPalettes) {
                    int currentPaletteIndex = 0;
                    resultPalette = null;
                    double bestDistance = 0;
                    for (HashMap<Integer, Integer> palette : palettes) {
                        // This calculates the total distance for all pixels in the tile using the closest matched colour for the palette
                        double colourDifference = 0;
                        for (int ty = 0 ; ty < tileHeight ; ty++) {
                            for (int tx = 0 ; tx < tileWidth ; tx++) {
                                Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                                if (colour != null) {
                                    if (forcedColourIndex.containsKey(colour)) {
                                        continue;
                                    }
                                    Integer closestColour = getBestPaletteColour(palette, colour);
                                    colourDifference += getColourDifference(new Color(closestColour) , new Color(colour));
                                }
                            }
                        }


                        if (resultPalette == null || colourDifference < bestDistance) {
                            resultPalette = palette;
                            bestDistance = colourDifference;
                            bestFoundPaletteIndex = currentPaletteIndex;
                        }
                        currentPaletteIndex++;
                    }
                } else {
                    int currentPaletteIndex = 0;
                    HashMap<Integer,Integer> bestFoundPalette = null;
                    int bestFoundNum = -1;
                    for (HashMap<Integer, Integer> palette : palettes) {
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

                    if (bestFoundPalette == null) {
                        bestFoundPaletteIndex = palettes.size();
                        resultPalette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                        palettes.add(resultPalette);
                    } else {
                        resultPalette = bestFoundPalette;
                    }

                    // Update any new colours into the best palette
                    for (Integer colour : usedColours) {
                        if (resultPalette.size() >= paletteMaxLen) {
                            break;
                        }
                        if (!resultPalette.containsKey(colour)) {
                            resultPalette.put(colour, resultPalette.size());
                        }
                    }
                }

                assert resultPalette.size() <= paletteMaxLen;

                // Now convert the tile to bitplane data based on the colours in the palette and the index values
                byte[] theTile = new byte[tileWidth * tileHeight];
                boolean theTileHasContents = false;
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        // If there is no colour then assume it's the first palette entry, which should be transparent
                        theTile[tx + (ty * tileWidth)] = 0;

                        // Then try to map the colour if it exists
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            Integer value = getBestPaletteIndex(resultPalette, colour);
                            if (value != null) {
                                theTile[tx + (ty * tileWidth)] = value.byteValue();
                                // Remove the pixel, since it has been processed
                                imageColours[x + tx + ((y + ty) * imageWidth)] = null;
                                if(value > 0) {
                                    theTileHasContents = true;
                                }
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

                if (outputTileBytes != null) {
                    tileHasData = theTileHasContents;
                }

                int bestTileIndex = currentTile;
                boolean bestFlipX = false;
                boolean bestFlipY = false;
                if ((outputScreenData != null) || ((outputSprites != null) && tileHasData)) {
                    String testTile = Base64.getEncoder().encodeToString(theTile);
                    TileIndexFlip existingIndexFlip = tileToIndexFlip.get(testTile);

                    if (existingIndexFlip != null) {
                        bestFlipX = existingIndexFlip.flipX;
                        bestFlipY = existingIndexFlip.flipY;
                        bestTileIndex = existingIndexFlip.index;
                        System.out.println("Found existing tile: " + bestTileIndex + " " + (bestFlipX?"H":"") + (bestFlipY?"V":""));
                    } else {
                        // Advances the tile number, could do with duplicate check here
                        tileByteData.put(theTile);
                        if (outputPlanes != null) {
                            for (int bp = 0; bp < numBitplanes; bp++) {
                                bitplaneData[bp].put(bitplaneDataTemp[bp]);
                            }
                        }

                        existingIndexFlip = new TileIndexFlip();
                        existingIndexFlip.flipX = false;
                        existingIndexFlip.flipY = false;
                        existingIndexFlip.index = currentTile;
                        tileToIndexFlip.put(testTile , existingIndexFlip);

                        // Now register the flips
                        byte[] theTileFlipped = new byte[tileWidth * tileHeight];
                        for (int ty = 0; ty < tileHeight ; ty++) {
                            for (int tx = 0; tx < tileWidth ; tx++) {
                                theTileFlipped[(tileWidth-1-tx) + (ty*tileWidth)] = theTile[tx + (ty*tileWidth)];
                            }
                        }
                        existingIndexFlip = new TileIndexFlip();
                        existingIndexFlip.flipX = true;
                        existingIndexFlip.flipY = false;
                        existingIndexFlip.index = currentTile;
                        testTile = Base64.getEncoder().encodeToString(theTileFlipped);
                        // Because the flipped versions might be symmetrical
                        if (!tileToIndexFlip.containsKey(testTile)) {
                            tileToIndexFlip.put(testTile, existingIndexFlip);
                        }

                        for (int ty = 0; ty < tileHeight ; ty++) {
                            for (int tx = 0; tx < tileWidth ; tx++) {
                                theTileFlipped[(tileWidth-1-tx) + ((tileHeight-1-ty)*tileWidth)] = theTile[tx + (ty*tileWidth)];
                            }
                        }
                        existingIndexFlip = new TileIndexFlip();
                        existingIndexFlip.flipX = true;
                        existingIndexFlip.flipY = true;
                        existingIndexFlip.index = currentTile;
                        testTile = Base64.getEncoder().encodeToString(theTileFlipped);
                        // Because the flipped versions might be symmetrical
                        if (!tileToIndexFlip.containsKey(testTile)) {
                            tileToIndexFlip.put(testTile, existingIndexFlip);
                        }

                        for (int ty = 0; ty < tileHeight ; ty++) {
                            for (int tx = 0; tx < tileWidth ; tx++) {
                                theTileFlipped[tx + ((tileHeight-1-ty)*tileWidth)] = theTile[tx + (ty*tileWidth)];
                            }
                        }
                        existingIndexFlip = new TileIndexFlip();
                        existingIndexFlip.flipX = false;
                        existingIndexFlip.flipY = true;
                        existingIndexFlip.index = currentTile;
                        testTile = Base64.getEncoder().encodeToString(theTileFlipped);
                        // Because the flipped versions might be symmetrical
                        if (!tileToIndexFlip.containsKey(testTile)) {
                            tileToIndexFlip.put(testTile, existingIndexFlip);
                        }

                        currentTile++;
                    }
                }

                if (bestFlipX) {
                    bestFoundPaletteIndex = bestFoundPaletteIndex | 0x40;
                    // Mode7 tiles want the flip in screen data
                    if (outputTileBytes != null) {
                        bestTileIndex = bestTileIndex  | 0x40;
                    }
                }
                if (bestFlipY) {
                    bestFoundPaletteIndex = bestFoundPaletteIndex | 0x80;
                    // Mode7 tiles want the flip in screen data
                    if (outputTileBytes != null) {
                        bestTileIndex = bestTileIndex  | 0x80;
                    }
                }

                byte theTileIndex = (byte) bestTileIndex;
                // TODO: Chars will need to be handled, for their chosen palette range
                byte theColour = (byte) (bestFoundPaletteIndex & (0x1f | 0x80 | 0x40));
                if (outputScreenData != null) {
                    screenTileData.put(theTileIndex);
                    screenColourData.put(theColour);
                } else if (outputSprites != null) {
                    if (tileHasData) {
                        if (currentTile >= 24) {
                            outputSprites.print(";");
                        }
                        outputSprites.println("b" + theTileIndex + ",b" + (theColour + paletteOffset) + ",b" + spriteYPos + ",b" + spriteXPos);
                        outputSprites2.println("\t+MEmitSpriteFrame " + theTileIndex + " , " + (theColour + paletteOffset));

                    } else {
                        outputSprites.println(";Empty");
                    }
                }


                // Now calculate if there is any data left
                usedColours.clear();
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            // Transparent forced colours should be ignored
                            if (forcedColourIndex.containsKey(colour)) {
                                if (forcedColourIndex.get(colour) == 0) {
                                    continue;
                                }
                            }
                            usedColours.add(colour);
                        }
                    }
                }
                System.out.println("usedColours size=" + usedColours.size());
                if (useStacking && usedColours.size() > 0) {
                    System.out.println(";Stacked x=" + x + " y=" + y);
                    if (outputSprites != null) {
                        outputSprites.println(";Stacked x=" + x + " y=" + y);
                    }
                    continue;
                }
                if (outputSprites != null) {
                    outputSprites2.println("\t+MEmitSpriteFrame_RestoreExit");
                    outputSprites2.println("");
                    newSprite = true;
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
        System.out.println("num tiles=" + currentTile);
    }

    private static Integer getBestPaletteIndex(HashMap<Integer, Integer> resultPalette, Integer colour) {
        if (forcedColourIndex.containsKey(colour)) {
            return forcedColourIndex.get(forcedColourIndex);
        }
        Integer result = resultPalette.get(colour);

        if (!fitPalettes) {
            return result;
        }

        if (result == null)
        {
            double bestDifference = 0;
            for (HashMap.Entry<Integer,Integer> entry : resultPalette.entrySet()) {
                double difference = getColourDifference(new Color(entry.getKey()) , new Color(colour));
                if (result == null || difference < bestDifference) {
                    result = entry.getValue();
                    bestDifference = difference;
                }
            }
        }

        return result;
    }

    private static Integer getBestPaletteColour(HashMap<Integer, Integer> resultPalette, Integer colour) {
        if (resultPalette.containsKey(colour)) {
            return colour;
        }

        if (!fitPalettes) {
            return null;
        }

        Integer result = null;

        double bestDifference = 0;
        for (HashMap.Entry<Integer,Integer> entry : resultPalette.entrySet()) {
            double difference = getColourDifference(new Color(entry.getKey()) , new Color(colour));
            if (result == null || difference < bestDifference) {
                result = entry.getKey();
                bestDifference = difference;
            }
        }

        return result;
    }


    private static void ImageQuantize() {
        System.out.println("Quantize...");

        // Quantize tiles down to a maximum number of colours
        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; x+= tileWidth) {
                HashMap<Integer,Double> usedColours = new HashMap<>();
                for (Integer colour : forcedColourIndex.keySet()) {
                    usedColours.put(colour , (double) (tileWidth*tileHeight)*10000);    // Significant weighting
                }
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            if (!usedColours.containsKey(colour)) {
                                usedColours.put(colour, 0.0);
                            } else {
                                Double num = usedColours.get(colour);
                                usedColours.put(colour, num+factorColourIndex.getOrDefault(colour, 1.0));
                            }
                        }
                    }
                }
                // Reduce until it fits
                while (usedColours.size() > paletteMaxQuantize) {
                    System.out.println("Reduce x=" + x + " y=" + y);
                    // Find the least used colour
                    Integer chosenMinColour = null;
                    double count = 0;
                    for (Map.Entry<Integer,Double> entry: usedColours.entrySet()) {
                        if (chosenMinColour == null || entry.getValue() < count) {
                            chosenMinColour = entry.getKey();
                            count = entry.getValue();
                        }
                    }

                    Color source = new Color(chosenMinColour);
                    Integer closestColourToMin = null;
                    double difference = -1;
                    for (Map.Entry<Integer,Double> entry: usedColours.entrySet()) {
                        // Skip the same colour
                        if (chosenMinColour.equals(entry.getKey())) {
                            continue;
                        }

                        Color destination = new Color(entry.getKey());
                        double thisDifference = getColourDifference(source, destination);

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

    private static double getColourDifference(Color source, Color destination) {
        if (useSquaredModel) {
            double thisDifference = (source.getRed() - destination.getRed()) * (source.getRed() - destination.getRed());
            thisDifference += (source.getGreen() - destination.getGreen()) * (source.getGreen() - destination.getGreen());
            thisDifference += (source.getBlue() - destination.getBlue()) * (source.getBlue() - destination.getBlue());
            return Math.sqrt(thisDifference);
        }
        double thisDifference = Math.abs(source.getRed() - destination.getRed());
        thisDifference += Math.abs(source.getGreen() - destination.getGreen());
        thisDifference += Math.abs(source.getBlue() - destination.getBlue());
        return thisDifference;
    }
}
