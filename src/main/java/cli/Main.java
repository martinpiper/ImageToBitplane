package cli;

import javafx.util.Pair;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

public class Main {

    static boolean[] allowedSizes = new boolean[65536];

    static int colourBitsRed = 4 , colourBitsGreen = 4 , colourBitsBlue = 4;
    static int colourShiftRed = 4 , colourShiftGreen = 4 , colourShiftBlue = 4;
    static int colourMaskRed = 0x0f , colourMaskGreen = 0x0f , colourMaskBlue = 0x0f;
    static int bytesPerColourPaletteEntry = 2;
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

    public static double ParseDoubleValueFrom(String value) {
        if (value.startsWith("0x")) {
            return (double)Integer.parseInt(value.substring(2) , 16);
        } else if (value.startsWith("$")) {
            return (double)Integer.parseInt(value.substring(1) , 16);
        }
        return Double.parseDouble(value);
    }

    static boolean useSquaredModel = true;
    static int paletteOffset = 0;
    static int paletteMaxLen = 8;
    static int paletteMinLen = 0;
    static int spriteXPos = 0;
    static int spriteYPos = 0xd0;
    static int paletteMaxQuantize = 32;
    static ArrayList<HashMap<Integer,Integer>> palettes = new ArrayList<>();
    static HashMap<Integer , Integer> forcedColourIndex = new HashMap<>();
    static HashMap<Integer , Integer> forcedColourByIndex = new HashMap<>();
    static int forcedColourIndexTransparent = -1;
    static HashMap<Integer , Double> factorColourIndex = new HashMap<>();
    static int tileWidth = 16 , tileHeight = 16;
    static int startX = 0 , startY = 0;
    static BufferedImage img = null;
    static int imageWidth = 0;
    static int imageHeight = 0;
    static Integer[] imageColours = null;
    static BufferedImage imageColoursResult = null;
    static BufferedImage imageColoursResultPre = null;
    static Integer[] imageColoursOriginal = null;
    static int numBitplanes = 3;
    static ByteBuffer[] bitplaneData = null;
    static ByteBuffer tileByteData = null;
    static SortedSet<Integer> usedColours = null;

    static int currentTile = 0;
    static ByteBuffer screenTileData = null;
    static ByteBuffer screenColourData = null;
    static String outputPlanes = null;
    static String outputTileBytes = null;
    static String outputScreenData = null;
    static PrintStream outputSprites = null;
    static PrintStream outputSprites2 = null;
    static PrintStream outputSprites3 = null;
    static PrintStream outputSprites4 = null;
    static LinkedList<Pair<String,Integer>> labelToValue = new LinkedList<>();
    static HashSet<Integer> labelValues = new HashSet<Integer>();
    static String outputPalettes = null;
    static String outputScaled = null;
    static String outputScaled4 = null;
    static boolean useStacking = false;
    static boolean fitPalettes = false;
    static boolean extraCharsBits = false;
    static boolean splitMaps = false;
    static int vectorLeftPalette = 0;
    static int vectorLeftLength = 0;
    static int vectorRightPalette = 0;
    static int vectorRightLength = 0;
    static String outputVectors = null;
    static ByteBuffer vectorData = null;

    static ArrayList<Region> regions = null;
    static boolean preserveData = false;
    static boolean regionShift = false;

    static String nameSuffix = "";

    static TreeMap<String , TileIndexFlip> tileToIndexFlip = new TreeMap<String , TileIndexFlip>();

    static boolean preferLaterPalettes = false;

    public static void main(String[] args) throws Exception {

        Arrays.fill(allowedSizes, Boolean.TRUE);
        boolean shiftTopLeft = false;
        boolean minimiseArea = false;
        boolean processNow = false;
        int transparentRGBValue = 0;
        boolean removeDuplicates = false;
        HashSet<String> removeDuplicatesNames = new HashSet<String>();

        for (int i = 0 ; i < args.length ; i++) {
            if (args[i].compareToIgnoreCase("--allowedSizesClear") == 0) {
                Arrays.fill(allowedSizes, 0 , 254, Boolean.FALSE);
                continue;
            }
            if (args[i].compareToIgnoreCase("--allowedSizesFileAdd") == 0) {
                i++;
                try {
                    List<String> lines = FileUtils.readLines(FileUtils.getFile(args[i]), StandardCharsets.UTF_8);
                    for (String line : lines) {
                        allowedSizes[Integer.parseInt(line)] = true;
                    }
                } catch (Exception e) {
                    System.out.println("Warning: File error for allowedSizesFileAdd " + e.getLocalizedMessage());
                    Arrays.fill(allowedSizes, Boolean.TRUE);
                }
                continue;
            }
            if (args[i].compareToIgnoreCase("--transparentRGB") == 0) {
                i++;
                Color transparentRGB = new Color(Integer.parseInt(args[i]) , Integer.parseInt(args[i+1]) , Integer.parseInt(args[i+2]));
                transparentRGBValue = transparentRGB.getRGB();
                i += 2;
                continue;
            }
            if (args[i].compareToIgnoreCase("--removeDuplicates") == 0) {
                removeDuplicates = true;
                continue;
            }
            if (args[i].compareToIgnoreCase("--shiftTopLeft") == 0) {
                shiftTopLeft = true;
                continue;
            }
            if (args[i].compareToIgnoreCase("--minimiseArea") == 0) {
                minimiseArea = true;
                continue;
            }
            if (args[i].compareToIgnoreCase("--processNow") == 0) {
                processNow = true;
                continue;
            }
            if (removeDuplicates) {
                if (Files.exists(Paths.get(args[i]))) {
                    removeDuplicatesNames.add(args[i]);
                }
                continue;
            }
            if (processNow) {
                if (Files.exists(Paths.get(args[i]))) {
                    boolean changed = false;
                    BufferedImage image = ImageIO.read(new File(args[i]));
                    if (shiftTopLeft) {
                        boolean abort = false;
                        while (!abort && image.getWidth() >= 2) {
                            for (int t = 0 ; t < image.getHeight() ; t++) {
                                if (image.getRGB(0 , t) != transparentRGBValue) {
                                    abort = true;
                                    break;
                                }
                            }
                            if (!abort) {
                                image = image.getSubimage(1,0,image.getWidth()-1, image.getHeight());
                                changed = true;
                            }
                        }
                        abort = false;
                        while (!abort && image.getHeight() >= 2) {
                            for (int t = 0 ; t < image.getWidth() ; t++) {
                                if (image.getRGB(t , 0) != transparentRGBValue) {
                                    abort = true;
                                    break;
                                }
                            }
                            if (!abort) {
                                image = image.getSubimage(0,1,image.getWidth(), image.getHeight()-1);
                                changed = true;
                            }
                        }
                    }
                    if (minimiseArea) {
                        int minWidth = image.getWidth();
                        int minHeight = image.getHeight();
                        boolean abort = false;
                        while (!abort && minWidth > 1) {
                            for (int t = 0 ; t < image.getHeight() ; t++) {
                                if (image.getRGB(minWidth-1 , t) != transparentRGBValue) {
                                    abort = true;
                                    break;
                                }
                            }
                            if (!abort) {
                                minWidth--;
                            }
                        }
                        abort = false;
                        while (!abort && minHeight > 1) {
                            for (int t = 0 ; t < image.getWidth() ; t++) {
                                if (image.getRGB(t , minHeight-1) != transparentRGBValue) {
                                    abort = true;
                                    break;
                                }
                            }
                            if (!abort) {
                                minHeight--;
                            }
                        }
                        if (minWidth != image.getWidth() || minHeight != image.getHeight()) {
                            image = image.getSubimage(0,0, minWidth, minHeight);
                            changed = true;
                        }
                    }

                    if (changed) {
                        System.out.println("Changed image: " + args[i]);
                        File file = new File(args[i]);
                        ImageIO.write(image , FilenameUtils.getExtension(args[i]), file);
                    } else {
                        System.out.println("No change: " + args[i]);
                    }
                }
                continue;
            }

            if (i+1 < args.length) {
                System.out.println("Considering arguments: " + args[i] + " " + args[i+1]);
            } else {
                System.out.println("Considering argument: " + args[i]);
            }
            if (args[i].compareToIgnoreCase("--rgbshift") == 0) {
                colourBitsRed = ParseValueFrom(args[i+1]);
                colourBitsGreen = ParseValueFrom(args[i+2]);
                colourBitsBlue = ParseValueFrom(args[i+3]);

                colourMaskRed = (1 << colourBitsRed) - 1;
                colourMaskGreen = (1 << colourBitsGreen) - 1;
                colourMaskBlue = (1 << colourBitsBlue) - 1;

                colourShiftRed = 8 - colourBitsRed;
                colourShiftGreen = 8 - colourBitsGreen;
                colourShiftBlue = 8 - colourBitsBlue;

                bytesPerColourPaletteEntry = ((colourBitsRed + colourBitsGreen + colourBitsBlue) + 7) / 8;

                i+=3;
                continue;
            } else if (args[i].compareToIgnoreCase("--newpalettes") == 0) {
                palettes = new ArrayList<>();
                continue;
            } else if (args[i].compareToIgnoreCase("--resetforcergb") == 0) {
                forcedColourIndex.clear();
                forcedColourByIndex.clear();
                forcedColourIndexTransparent = -1;
                continue;
            } else if (args[i].compareToIgnoreCase("--forcergb") == 0) {
                int theColour = ApplyColorLimitsFromColour(new Color(ParseValueFrom(args[i+1]), ParseValueFrom(args[i+2]), ParseValueFrom(args[i+3]))).getRGB();
                if (forcedColourIndex.size() == 0) {
                    forcedColourIndexTransparent = theColour;
                }
                forcedColourByIndex.put(forcedColourIndex.size() , theColour);
                forcedColourIndex.put(theColour , forcedColourIndex.size());
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
            } else if (args[i].compareToIgnoreCase("--minpalettesize") == 0) {
                paletteMinLen = ParseValueFrom(args[i+1]);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--paletteaddrgbs") == 0) {
                i++;
                HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                // Process command line triplets
                while (!args[i].startsWith("--")) {
                    Color rgb = new Color(ParseValueFrom(args[i]) , ParseValueFrom(args[i+1]) , ParseValueFrom(args[i+2]));
                    int theColour = ApplyColorLimitsFromColour(rgb).getRGB();
                    if (!palette.containsKey(theColour)) {
                        palette.put(theColour, palette.size());
                    }
                    i+=3;
                }
                palettes.add(palette);
                i--; // Ready for the next parameter
                continue;
            } else if (args[i].compareToIgnoreCase("--loadpaletteraw") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                int counter = 0;
                HashSet<Integer> alreadyLoaded = new HashSet<>();
                for (int j = 0; j < bytes.length; j += bytesPerColourPaletteEntry) {
                    int rgb = getRGBFromPaletteBytes(bytes, j);
                    if (!palette.containsKey(rgb)) {    // Fixes the load problem when black (0,0,0) appears multiple times for a palette
                        palette.put(rgb, palette.size());
                    }
                    counter++;
                    if (counter >= paletteMaxLen) {
                        palettes.add(palette);
                        palette = new HashMap<Integer, Integer>();
                        counter = 0;
                    }
                }
                if (!palette.isEmpty()) {
                    palettes.add(palette);
                }

                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--loadpalette") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                if (bytes.length / bytesPerColourPaletteEntry <= paletteMaxLen) {
                    HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                    // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    for (int j = 0; j < bytes.length; j += bytesPerColourPaletteEntry) {
                        int rgb = getRGBFromPaletteBytes(bytes, j);
                        if (!palette.containsKey(rgb)) {
                            palette.put(rgb, j / bytesPerColourPaletteEntry);
                        }
                    }
                    palettes.add(palette);
                } else {
                    HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                    // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    for (int j = 0; j < bytes.length; j += bytesPerColourPaletteEntry) {
                        int rgb = getRGBFromPaletteBytes(bytes, j);
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
            } else if (args[i].compareToIgnoreCase("--onlyloadwholepalettes") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                HashMap<Integer, Integer> palette = new HashMap<Integer, Integer>();
                // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                for (int j = 0; j < bytes.length; j += bytesPerColourPaletteEntry) {
                    int rgb = getRGBFromPaletteBytes(bytes, j);
                    if (!palette.containsKey(rgb)) {
                        palette.put(rgb, palette.size());
                    }
                    if (palette.size() >= paletteMaxLen) {
                        palettes.add(palette);
                        palette = new HashMap<Integer, Integer>();
                        // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                    }
                }

                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--loadpalettebestfit") == 0) {
                byte[] bytes = Files.readAllBytes(Paths.get(args[i+1]));

                // TODO: palette = (HashMap<Integer, Integer>) forcedColourIndex.clone();
                for (int j = 0; j < bytes.length; j += bytesPerColourPaletteEntry) {
                    int rgb = getRGBFromPaletteBytes(bytes, j);

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
            } else if (args[i].compareToIgnoreCase("--imagescale") == 0) {
                double scaleX = ParseDoubleValueFrom(args[i+1]);
                double scaleY = ParseDoubleValueFrom(args[i+2]);
                ImageScale(scaleX , scaleY);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--imagequantize") == 0) {
                paletteMaxQuantize = ParseValueFrom(args[i+1]);
                ImageQuantize();
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--batchimagequantize") == 0) {
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
            } else if (args[i].compareToIgnoreCase("--imagewh") == 0) {
                imageWidth = ParseValueFrom(args[i+1]);
                imageHeight = ParseValueFrom(args[i+2]);
                i+=2;
                continue;
            } else if (args[i].compareToIgnoreCase("--image") == 0) {
                img = ImageIO.read(new File(args[i+1]));
                i++;

                setupInputImage();
                continue;
            } else if (args[i].compareToIgnoreCase("--batchimages") == 0) {
                i++;
                while (Files.exists(Paths.get(args[i]))) {
                    System.out.println("Processing: " + args[i]);
                    File file = new File(args[i]);
                    nameSuffix = file.getName().replace(" " , "_").replace("-" , "_");
                    nameSuffix = "_" + nameSuffix.substring(0 , nameSuffix.indexOf(".")) + "_";
                    img = ImageIO.read(file);
                    setupInputImage();

                    if (paletteMaxQuantize < 32) {
                        ImageQuantize();
                    }

                    TileConvert();

                    preserveData = true;
                    i++;
                }
                i--;    // And backwards for the next command line entry
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
            } else if (args[i].compareToIgnoreCase("--removeregions") == 0) {
                regions = null;
                continue;
            } else if (args[i].compareToIgnoreCase("--preservedata") == 0) {
                preserveData = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--nopreservedata") == 0) {
                preserveData = false;
                continue;
            } else if (args[i].compareToIgnoreCase("--nowrite") == 0) {
                outputScreenData = null;
                outputSprites = null;
                outputSprites2 = null;
                outputSprites3 = null;
                outputSprites4 = null;
                outputPlanes = null;
                outputPalettes = null;
                outputTileBytes = null;
                currentTile = 0;
                outputScaled = null;
                outputScaled4 = null;
                outputVectors = null;
                continue;
            } else if (args[i].compareToIgnoreCase("--nowritepass") == 0) {
                outputScreenData = null;
                outputSprites = null;
                outputSprites2 = null;
                outputSprites3 = null;
                outputSprites4 = null;
                cumulativeSpriteCount = 0;
                outputPlanes = null;
                outputPalettes = null;
                outputTileBytes = null;
                currentTile = 0;
                outputScaled = null;
                outputScaled4 = null;
                outputVectors = null;
                TileConvert();
                continue;
            } else if (args[i].compareToIgnoreCase("--outputvectors") == 0) {
                vectorLeftPalette = ParseValueFrom(args[i+1]);
                i++;
                vectorLeftLength = ParseValueFrom(args[i+1]);
                i++;
                vectorRightPalette = ParseValueFrom(args[i+1]);
                i++;
                vectorRightLength = ParseValueFrom(args[i+1]);
                i++;
                outputVectors = args[i+1];
                vectorData = ByteBuffer.allocate(16384);
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputplanes") == 0) {
                outputPlanes = args[i+1];
                outputTileBytes = null;
                outputScaled = null;
                outputScaled4 = null;
                outputVectors = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputscaled") == 0) {
                outputPlanes = null;
                outputTileBytes = null;
                outputScaled = args[i+1];
                outputScaled4 = null;
                outputVectors = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputscaled4") == 0) {
                outputPlanes = null;
                outputTileBytes = null;
                outputScaled = null;
                outputScaled4 = args[i+1];
                outputVectors = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputtilebytes") == 0) {
                outputTileBytes = args[i+1];
                outputPlanes = null;
                outputScaled = null;
                outputScaled4 = null;
                outputVectors = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputscrcol") == 0) {
                outputScreenData = args[i+1];
                outputScaled = null;
                outputScaled4 = null;
                outputVectors = null;
                i++;
                continue;
            } else if (args[i].compareToIgnoreCase("--outputsprites") == 0) {
                outputSprites = new PrintStream(new FileOutputStream(args[i+1]));
                outputSprites2 = new PrintStream(new FileOutputStream(args[i+1] + ".a"));
                outputSprites3 = new PrintStream(new FileOutputStream(args[i+1] + "Vars.a"));
                outputSprites4 = new PrintStream(new FileOutputStream(args[i+1] + "Tables.a"));
                cumulativeSpriteCount = 0;
                outputVectors = null;
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
            } else if (args[i].compareToIgnoreCase("--writepass") == 0) {
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
            } else if (args[i].compareToIgnoreCase("--nosplitmaps") == 0) {
                splitMaps = false;
                continue;
            } else if (args[i].compareToIgnoreCase("--splitmaps") == 0) {
                splitMaps = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--chars") == 0) {
                extraCharsBits = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--nochars") == 0) {
                extraCharsBits = false;
                continue;
            } else if (args[i].compareToIgnoreCase("--preferLaterPalettes") == 0) {
                preferLaterPalettes = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--nopreferLaterPalettes") == 0) {
                preferLaterPalettes = false;
                continue;
            } else if (args[i].compareToIgnoreCase("--concat") == 0) {
                i++;
                String filename1 = args[i];
                i++;
                String filename2 = args[i];
                i++;
                String filenameOutput = args[i];

                BufferedImage image1 = ImageIO.read(new File(filename1));
                BufferedImage image2 = ImageIO.read(new File(filename2));

                BufferedImage output = new BufferedImage(Math.max(image1.getWidth(), image2.getWidth())
                         , image1.getHeight() + image2.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics g = output.getGraphics();
                g.drawImage(image1,0,0,null);
                g.drawImage(image2,0,image1.getHeight(),null);
                ImageIO.write(output,"png", new File(filenameOutput));
                continue;
            } else if (args[i].compareToIgnoreCase("--concath") == 0) {
                i++;
                String filename1 = args[i];
                i++;
                String filename2 = args[i];
                i++;
                String filenameOutput = args[i];

                BufferedImage image1 = ImageIO.read(new File(filename1));
                BufferedImage image2 = ImageIO.read(new File(filename2));

                BufferedImage output = new BufferedImage(image1.getWidth() + image2.getWidth(), Math.max(image1.getHeight(), image2.getHeight()), BufferedImage.TYPE_INT_RGB);
                Graphics g = output.getGraphics();
                g.drawImage(image1,0,0,null);
                g.drawImage(image2,image1.getWidth(),0,null);
                ImageIO.write(output,"png", new File(filenameOutput));
                continue;
            } else if (args[i].compareToIgnoreCase("--planestoscaled") == 0) {
                i++;
                String filename1 = args[i];
                i++;
                String filename2 = args[i];
                i++;
                String filename3 = args[i];
                i++;
                String filename4 = args[i];
                i++;
                String filenameOutput = args[i];

                InputStream inputStream1 = new FileInputStream(filename1);
                InputStream inputStream2 = new FileInputStream(filename2);
                InputStream inputStream3 = new FileInputStream(filename3);
                InputStream inputStream4 = new FileInputStream(filename4);

                OutputStream output = new FileOutputStream(filenameOutput + "0.bin");

                byte bytes1[] = new byte[64];
                byte bytes2[] = new byte[64];
                byte bytes3[] = new byte[64];
                byte bytes4[] = new byte[64];
                byte outChunk[] = new byte[256];
                int readBytes = 0;
                int writeBytes = 0;
                int fileNum = 1;
                while ( (readBytes = inputStream1.read(bytes1)) >= 0) {
                    inputStream2.read(bytes2);
                    inputStream3.read(bytes3);
                    inputStream4.read(bytes4);


                    int xp = 0 , yp = 0;
                    for(int a = 0 ; a < 4 ; a++) {
                        switch (a) {
                            case 0:
                            default:
                                xp = 0;
                                yp = 0;
                                break;
                            case 1:
                                xp = 8;
                                yp = 0;
                                break;
                            case 2:
                                xp = 0;
                                yp = 8;
                                break;
                            case 3:
                                xp = 8;
                                yp = 8;
                                break;
                        }

                        for (int b = 0 ; b < 8 ; b++) {
                            for (int c = 0 ; c < 8 ; c++) {
                                int cb = 7-c;
                                int pixel = 0;
                                if ((bytes1[(a*8) + b] & (1<<cb)) > 0) {
                                    pixel |= 0x01;
                                }
                                if ((bytes2[(a*8) + b] & (1<<cb)) > 0) {
                                    pixel |= 0x02;
                                }
                                if ((bytes3[(a*8) + b] & (1<<cb)) > 0) {
                                    pixel |= 0x04;
                                }
                                if ((bytes4[(a*8) + b] & (1<<cb)) > 0) {
                                    pixel |= 0x08;
                                }
                                if (readBytes == 64) {
                                    if ((bytes1[32 + (a * 8) + b] & (1 << cb)) > 0) {
                                        pixel |= 0x10;
                                    }
                                    if ((bytes2[32 + (a * 8) + b] & (1 << cb)) > 0) {
                                        pixel |= 0x20;
                                    }
                                    if ((bytes3[32 + (a * 8) + b] & (1 << cb)) > 0) {
                                        pixel |= 0x40;
                                    }
                                    if ((bytes4[32 + (a * 8) + b] & (1 << cb)) > 0) {
                                        pixel |= 0x80;
                                    }
                                }

                                outChunk[(xp+c) + ((yp + b)*16)] = (byte) pixel;
                            }
                        }
                    }

                    output.write(outChunk);
                    writeBytes += outChunk.length;
                    if (writeBytes >= 8192) {
                        output.close();
                        output = new FileOutputStream(filenameOutput + (fileNum++) + ".bin");
                        writeBytes = 0;
                    }

                }

                output.close();
                continue;
            } else if (args[i].compareToIgnoreCase("--region") == 0) {
                // --region redframe2 70 40 -1 -11 32 32
                Region region = new Region();
                i++;
                region.name = args[i];
                i++;
                int hotX = ParseValueFrom(args[i]);
                i++;
                int hotY = ParseValueFrom(args[i]);
                i++;
                region.rect.x = hotX + ParseValueFrom(args[i]);
                region.offsetX = ParseValueFrom(args[i]);
                i++;
                region.rect.y = hotY + ParseValueFrom(args[i]);
                region.offsetY = ParseValueFrom(args[i]);
                i++;
                region.rect.width = ParseValueFrom(args[i]);
                i++;
                region.rect.height = ParseValueFrom(args[i]);
                region.regionShift = regionShift;


                if (regions == null) {
                    regions = new ArrayList<>();
                }
                regions.add(region);
                continue;
            } else if (args[i].compareToIgnoreCase("--regionxy") == 0) {
                // --regionxy redframe2 70 40 69 29 100 60
                Region region = new Region();
                i++;
                region.name = args[i];
                i++;
                int hotX = ParseValueFrom(args[i]);
                i++;
                int hotY = ParseValueFrom(args[i]);
                i++;
                region.rect.x = ParseValueFrom(args[i]);
                i++;
                region.rect.y = ParseValueFrom(args[i]);
                i++;
                region.rect.width = (ParseValueFrom(args[i]) - region.rect.x) + 1;
                i++;
                region.rect.height = (ParseValueFrom(args[i]) - region.rect.y) + 1;
                region.regionShift = regionShift;

                region.offsetX = -(hotX - region.rect.x);
                region.offsetY = -(hotY - region.rect.y);

                if (regions == null) {
                    regions = new ArrayList<>();
                }
                regions.add(region);
                continue;
            } else if (args[i].compareToIgnoreCase("--regionshift") == 0) {
                regionShift = true;
                continue;
            } else if (args[i].compareToIgnoreCase("--noregionshift") == 0) {
                regionShift = false;
                continue;
            } else if (args[i].compareToIgnoreCase("--namesuffix") == 0) {
                i++;
                nameSuffix = args[i];
                continue;
            } else if (args[i].compareToIgnoreCase("--nonamesuffix") == 0) {
                nameSuffix = "";
                continue;
            }
            System.err.println("Unknown option: " + args[i]);
        }


        if (removeDuplicates) {
            Map<String, BufferedImage> nameToBufferedImage = new HashMap<>();
            Map<String, Integer> nameToWidth = new HashMap<>();
            Map<String, Integer> nameToHeight = new HashMap<>();
            for (String filename : removeDuplicatesNames) {
                if (!Files.exists(Paths.get(filename))) {
                    continue;
                }

                System.out.println("Consider duplicate removal: " + filename);
                BufferedImage image1 = nameToBufferedImage.get(filename);
                if (image1 == null) {
//                    System.out.println("Used cache: " + filename);
                    image1 = ImageIO.read(new File(filename));
                    nameToBufferedImage.put(filename, image1);
                    nameToWidth.put(filename, image1.getWidth());
                    nameToHeight.put(filename, image1.getHeight());
                }
                for (String otherFilename : removeDuplicatesNames) {
                    if (filename.equals(otherFilename)) {
                        continue;
                    }
                    if (image1.getWidth() > nameToWidth.getOrDefault(otherFilename , Integer.MAX_VALUE)) {
                        continue;
                    }
                    if (image1.getHeight() > nameToHeight.getOrDefault(otherFilename , Integer.MAX_VALUE)) {
                        continue;
                    }
                    if (!Files.exists(Paths.get(otherFilename))) {
                        continue;
                    }
//                    System.out.println("Consider duplicate removal with: " + otherFilename);
                    BufferedImage image2 = nameToBufferedImage.get(otherFilename);
                    if (image2 == null) {
//                        System.out.println("Used cache: " + otherFilename);
                        image2 = ImageIO.read(new File(otherFilename));
                        nameToBufferedImage.put(otherFilename, image2);
                        nameToWidth.put(otherFilename, image2.getWidth());
                        nameToHeight.put(otherFilename, image2.getHeight());
                    }
                    // If image1 is a sub-image of image2 then still compare it and remove if needed
                    if (image1.getWidth() > image2.getWidth()) {
                        continue;
                    }
                    if (image1.getHeight() > image2.getHeight()) {
                        continue;
                    }
                    boolean identical = true;
                    for (int x = 0; x < image1.getWidth() && identical; x++) {
                        for (int y = 0; y < image1.getHeight() && identical; y++) {
                            if (image1.getRGB(x, y) == image2.getRGB(x, y)) {
                                continue;
                            }
                            // And flips
                            if (image1.getRGB(x, y) == image2.getRGB(image2.getWidth() - x - 1, y)) {
                                continue;
                            }
                            if (image1.getRGB(x, y) == image2.getRGB(x, image2.getHeight() - y - 1)) {
                                continue;
                            }
                            if (image1.getRGB(x, y) == image2.getRGB(image2.getWidth() - x - 1, image2.getHeight() - y - 1)) {
                                continue;
                            }
                            identical = false;
                            break;
                        }
                    }
                    if (identical) {
                        FileUtils.deleteQuietly(new File(filename));
                        System.out.println("Removing identical image: " + filename);
                        break;
                    }
                }
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

    private static void setupInputImage() {

        while (!allowedSizes[img.getWidth()]) {
            System.out.println("   Expand width from " + img.getWidth());
            BufferedImage newImage = new BufferedImage(img.getWidth() + 1, img.getHeight(), BufferedImage.TYPE_INT_RGB);
            final int[] firstTransColour = {0};
            if (!forcedColourByIndex.isEmpty()) {
                firstTransColour[0] = forcedColourByIndex.get(0);
            } else if (!palettes.isEmpty()) {
                HashMap<Integer, Integer> thePalette = palettes.get(0);
                thePalette.forEach((k, v) -> {
                    if (v == 0) {
                        firstTransColour[0] = k;
                    }
                });
            }
            for (int x = 0; x < newImage.getWidth(); x++) {
                for (int y = 0; y < newImage.getHeight(); y++) {
                    newImage.setRGB(x, y, firstTransColour[0]);
                }
            }
            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    newImage.setRGB(x, y, img.getRGB(x, y));
                }
            }
            img = newImage;
        }
        while (!allowedSizes[img.getHeight()]) {
            System.out.println("   Expand height from " + img.getHeight());
            BufferedImage newImage = new BufferedImage(img.getWidth(), img.getHeight()+1, BufferedImage.TYPE_INT_RGB);
            final int[] firstTransColour = {0};
            if (!forcedColourByIndex.isEmpty()) {
                firstTransColour[0] = forcedColourByIndex.get(0);
            } else if (!palettes.isEmpty()) {
                HashMap<Integer, Integer> thePalette = palettes.get(0);
                thePalette.forEach((k, v) -> {
                    if (v == 0) {
                        firstTransColour[0] = k;
                    }
                });
            }
            for (int x = 0; x < newImage.getWidth(); x++) {
                for (int y = 0; y < newImage.getHeight(); y++) {
                    newImage.setRGB(x, y, firstTransColour[0]);
                }
            }
            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    newImage.setRGB(x, y, img.getRGB(x, y));
                }
            }
            img = newImage;
        }

        regions = null;
        regionShift = false;

        imageWidth = img.getWidth();
        imageHeight = img.getHeight();
        imageColours = new Integer[imageWidth * imageHeight];
        imageColoursResult = new BufferedImage(imageWidth , imageHeight , BufferedImage.TYPE_INT_RGB);
        imageColoursResultPre = new BufferedImage(imageWidth , imageHeight , BufferedImage.TYPE_INT_RGB);
        // Applies target platform colour limits first
        for (int y = 0 ; y < imageHeight ; y++) {
            for (int x = 0; x < imageWidth; x++) {
                Color colour = new Color(img.getRGB(x,y));
                Color newColour = ApplyColorLimitsFromColour(colour);
                imageColours[x+(y*imageWidth)] = newColour.getRGB();
                imageColoursResultPre.setRGB(x,y,newColour.getRGB());
            }
        }
        imageColoursOriginal = imageColours.clone();
        tileWidth = imageWidth;
        tileHeight = imageHeight;
    }

    private static int getRGBFromPaletteBytes(byte[] bytes, int j) {
        int colour = ((int)bytes[j]) & 0xff;
        if (bytesPerColourPaletteEntry > 1) {
            colour |= (((int)bytes[j + 1]) & 0xff) << 8;
            if (bytesPerColourPaletteEntry > 2) {
                colour |= (((int)bytes[j + 2]) & 0xff) << 16;
            }
        }

        int red = (colour & colourMaskRed) << colourShiftRed;
        int green = ((colour >> colourBitsRed) & colourMaskGreen) << colourShiftGreen;
        int blue = ((colour >> (colourBitsRed + colourBitsGreen)) & colourMaskBlue) << colourShiftBlue;

        int rgb = new Color(red, green, blue).getRGB();
        return rgb;
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
        PrintStream fcd;
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

            ImageIO.write(imageColoursResult , "png" , new File(outputTileBytes + ".png"));
            ImageIO.write(imageColoursResultPre , "png" , new File(outputTileBytes + "_pre.png"));
        }

        if (outputScaled != null) {
            tileByteData.flip();
            byte[] arr = new byte[tileByteData.remaining()];
            tileByteData.get(arr);
            int index = 0;
            int sprPos = 0;
            fc = null;
            while (sprPos < arr.length) {
                byte[] merged = new byte[1024];
                for (int a = 0 ; a < 1024 ; a++) {
                    merged[a] = arr[sprPos + a];
                    // Is there another sprite, if yes merge it?
                    if ( (sprPos + 1024) < arr.length) {
                        merged[a] |= arr[sprPos + 1024 + a] << 4;
                    }
                }

                if ( (sprPos + 1024) < arr.length) {
                    sprPos += 1024;
                }
                if (fc == null) {
                    fc = new FileOutputStream(outputScaled + index + ".bin").getChannel();
                    index++;
                }
                fc.write(ByteBuffer.wrap(merged));
                if (fc.size() >= 8192) {
                    fc.close();
                    fc = null;
                }

                sprPos += 1024;
            }
            ImageIO.write(imageColoursResult , "png" , new File(outputScaled + ".png"));
            ImageIO.write(imageColoursResultPre , "png" , new File(outputScaled + "_pre.png"));
        }

        if (outputScaled4 != null) {
            tileByteData.flip();
            byte[] arr = new byte[tileByteData.remaining()];
            tileByteData.get(arr);
            int sprPos = 0;

            byte[] merged = new byte[(arr.length+1)/2];
            int storePos = 0;
            // First bank
            for (int i = 0 ; i < merged.length ; i++) {
                if (((i*2)+1) < arr.length) {
                    merged[i] = (byte) (arr[(i*2)] | (arr[(i*2)+1] << 4));
                } else {
                    merged[i] = (byte) arr[(i*2)];
                }
            }

            int fileIndex = 0;
            int chunkOffset = 0;
            while (chunkOffset < merged.length) {
                if (fileIndex == 0) {
                    fc = new FileOutputStream(outputScaled4 + ".bin").getChannel();
                } else {
                    fc = new FileOutputStream(outputScaled4 + ".bin" + fileIndex).getChannel();
                }
                int minLength = Math.min(merged.length - chunkOffset , 0x10000);
                fc.write(ByteBuffer.wrap(merged, chunkOffset , minLength));

                fc.close();
                fc = null;
                chunkOffset += 0x10000;
                fileIndex++;
            }

            ImageIO.write(imageColoursResult , "png" , new File(outputScaled4 + ".png"));
            ImageIO.write(imageColoursResultPre , "png" , new File(outputScaled4 + "_pre.png"));


            // Now output suitable ScaleTab code
            //outputSprites4
            for(Integer value : labelValues) {
                for (Pair<String, Integer> entry : labelToValue) {
                    if (entry.getValue().equals(value)) {
                        outputSprites4.println(entry.getKey()+"ScaleTab");
                    }
                }
                outputSprites4.println("\t+MGenerateSprite4ScaleTable " + value);
            }

        }

        if (outputScreenData != null || outputSprites != null) {
            for (int bp = 0; bp < numBitplanes; bp++) {
                if (outputPlanes != null) {
                    fc = new FileOutputStream(outputPlanes + bp + ".bin").getChannel();
                    bitplaneData[bp].flip();
                    fc.write(bitplaneData[bp]);
                    fc.close();
                }
            }
            if (outputPlanes != null) {
                ImageIO.write(imageColoursResult , "png" , new File(outputPlanes + ".png"));
                ImageIO.write(imageColoursResultPre , "png" , new File(outputPlanes + "_pre.png"));
            }
        }

        if (outputVectors != null) {
            fc = new FileOutputStream(outputVectors).getChannel();
            vectorData.flip();
            fc.write(vectorData);
            fc.close();
            ImageIO.write(imageColoursResult , "png" , new File(outputVectors + ".png"));
            ImageIO.write(imageColoursResultPre , "png" , new File(outputVectors + "_pre.png"));
        }

        if (outputScreenData != null) {
            fc = new FileOutputStream(outputScreenData).getChannel();
            screenTileData.flip();
            fc.write(screenTileData);
            if (outputTileBytes == null) {
                if (splitMaps) {
                    fc = new FileOutputStream(outputScreenData + "2").getChannel();
                }
                screenColourData.flip();
                fc.write(screenColourData);
            }
            fc.close();
        }

        if (outputPalettes != null) {
            fc = new FileOutputStream(outputPalettes).getChannel();
            fcd = new PrintStream(new FileOutputStream(outputPalettes + ".debug.txt"));
            System.out.println("num palettes=" + palettes.size());
            int outNum = 0;
            for (HashMap<Integer, Integer> palette : palettes) {
                System.out.println("palette size=" + palette.size());
                if (outNum < 32) {
                    int maxEntryIndex = paletteMinLen;
                    if (palettes.size() == 1) {
                        for (Map.Entry<Integer, Integer> entry : palette.entrySet()) {
                            if ((entry.getValue()+1) > maxEntryIndex) {
                                maxEntryIndex = entry.getValue() + 1;
                            }
                        }
                    } else {
                        maxEntryIndex = paletteMaxLen;
                    }
                    byte[] thisPalette = new byte[maxEntryIndex * bytesPerColourPaletteEntry];
                    Color[] thisPaletteDebug = new Color[maxEntryIndex];
                    for (Map.Entry<Integer, Integer> entry : palette.entrySet()) {
                        Color colour = new Color(entry.getKey());

                        // Construct the packed value in the correct order
                        int theColourValue = colour.getRed() >> colourShiftRed;
                        theColourValue |= (colour.getGreen() >> colourShiftGreen) << colourBitsRed;
                        theColourValue |= (colour.getBlue() >> colourShiftBlue) << (colourBitsRed + colourBitsGreen);

                        // Add to the palette data
                        thisPalette[(entry.getValue() * bytesPerColourPaletteEntry)] = (byte) (theColourValue & 0xff);
                        thisPaletteDebug[entry.getValue()] = colour;
                        if (bytesPerColourPaletteEntry > 1) {
                            theColourValue >>= 8;
                            thisPalette[(entry.getValue() * bytesPerColourPaletteEntry)+1] = (byte) (theColourValue & 0xff);
                            if (bytesPerColourPaletteEntry > 2) {
                                theColourValue >>= 8;
                                thisPalette[(entry.getValue() * bytesPerColourPaletteEntry)+2] = (byte) (theColourValue & 0xff);
                            }
                        }
                    }
                    fc.write(ByteBuffer.wrap(thisPalette));
                    fcd.print("Palette " + outNum + " : ");
                    int index = 0;
                    while (index < thisPaletteDebug.length) {
                        if (thisPaletteDebug[index] == null) {
                            fcd.print(" " + index + ":unused");
                        } else {
                            fcd.print(" " + index + ":" + thisPaletteDebug[index].getRed() + "," + thisPaletteDebug[index].getGreen() + "," + thisPaletteDebug[index].getBlue());
                        }
                        index++;
                    }
                    fcd.println();
                }
                outNum++;
            }
            fc.close();
        }
    }

    static PrintStream fcdTileScreen;
    private static void TileConvert() throws FileNotFoundException {
        System.out.println("To tiles...");
        fcdTileScreen = null;
        if (outputScreenData != null) {
            fcdTileScreen = new PrintStream(new FileOutputStream(outputScreenData + ".debug.txt"));
        }
        if (!preserveData) {
            screenTileData = ByteBuffer.allocate((imageWidth * imageHeight) / tileWidth / tileHeight);
            screenColourData = ByteBuffer.allocate((imageWidth * imageHeight) / tileWidth / tileHeight);
            tileByteData = ByteBuffer.allocate(tileWidth * tileHeight * 1024);  // Ample space even for extended character data
        } else {
            ByteBuffer temp = ByteBuffer.allocate((imageWidth * imageHeight) / tileWidth / tileHeight);
            if (temp.capacity() >= screenTileData.capacity()) {
                screenTileData.flip();
                temp.put(screenTileData);
                screenTileData = temp;
            }

            temp = ByteBuffer.allocate((imageWidth * imageHeight) / tileWidth / tileHeight);
            if (temp.capacity() >= screenColourData.capacity()) {
                screenColourData.flip();
                temp.put(screenColourData);
                screenColourData = temp;
            }
        }

        boolean newSprite = true;

        ensureRegions();

        int prevY = regions.get(0).rect.y;

        int regionIndex = 0;

        while (regionIndex < regions.size()) {
            Region region = regions.get(regionIndex);
            int x = region.rect.x;
            int y = region.rect.y;

            if (!(x >= startX && y >= startY && x < imageWidth && y < imageHeight)) {
                continue;
            }

            newSprite = processTileImageAt(newSprite, x, y, region);

            if (useStacking && usedColours.size() > 0) {
                System.out.println("usedColours size=" + usedColours.size());
                System.out.println(";Stacked x=" + x + " y=" + y);
                if (outputSprites != null) {
                    outputSprites.println(";Stacked x=" + x + " y=" + y);
                }
                continue;
            }
            if (outputSprites != null && region.end) {
                outputSprites2.println("\t+MEmitSpriteFrame_RestoreExit");
                outputSprites2.println("");
                newSprite = true;

                outputSprites3.println(lastGoodSpriteName + "_count = " + cumulativeSpriteCount);
                cumulativeSpriteCount = 0;

            }

            regionIndex++;
            x+=tileWidth;

            if (outputSprites != null) {
                spriteXPos += tileWidth;
                if (spriteXPos >= 256) {
                    spriteXPos = 0;
                    spriteYPos -= tileHeight;
                }
            }

            // Detect a new row being converted
            if (prevY != region.rect.y) {
                prevY = region.rect.y;
                if (outputSprites != null) {
                    spriteXPos = 0;
                    spriteYPos -= tileHeight;
                }
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

    private static void ensureRegions() {
        // If there are existing regions, then ensure we have enough regions at the tile size to cover all the data
        if (regions != null && !regions.isEmpty()) {

            ArrayList<Region> newRegions = new ArrayList<>();

            for (Region region : regions) {

                if (region.rect.width > tileWidth || region.rect.height > tileHeight) {
                    // If region optimisation is enabled, move the region to the top left to the transparent colour rules
                    if (region.regionShift) {
                        // --regionshift
                        // Check left shift for region width maximum attempts
                        for (int s = 0 ; s < region.rect.width - 1 ; s++) {
                            boolean isTransparent = true;
                            for (int i = 0; isTransparent && i < region.rect.height; i++) {
                                if (imageColours[region.rect.x + ((region.rect.y + i) * imageWidth)] != forcedColourIndexTransparent) {
                                    isTransparent = false;
                                }
                            }
                            if (isTransparent) {
                                for (int i = 0; i < region.rect.height; i++) {
                                    for (int j = 0; j < region.rect.width - 1; j++) {
                                        imageColours[region.rect.x  + j + ((region.rect.y + i) * imageWidth)] = imageColours[region.rect.x + j + 1 + ((region.rect.y + i) * imageWidth)];
                                    }
                                    imageColours[region.rect.x  + region.rect.width-1 + ((region.rect.y + i) * imageWidth)] = forcedColourIndexTransparent;
                                }
                                region.offsetX++;
                            }
                        }

                        // Check up shift for region height maximum attempts
                        for (int s = 0 ; s < region.rect.height - 1 ; s++) {
                            boolean isTransparent = true;
                            for (int i = 0; isTransparent && i < region.rect.width; i++) {
                                if (imageColours[region.rect.x + i + (region.rect.y * imageWidth)] != forcedColourIndexTransparent) {
                                    isTransparent = false;
                                }
                            }
                            if (isTransparent) {
                                for (int i = 0; i < region.rect.height - 1; i++) {
                                    for (int j = 0; j < region.rect.width - 1; j++) {
                                        imageColours[region.rect.x  + j + ((region.rect.y + i) * imageWidth)] = imageColours[region.rect.x + j + ((region.rect.y + i + 1) * imageWidth)];
                                    }
                                }
                                for (int j = 0; j < region.rect.width - 1; j++) {
                                    imageColours[region.rect.x + j + ((region.rect.y + region.rect.height - 1) * imageWidth)] = forcedColourIndexTransparent;
                                }
                                region.offsetY++;
                            }
                        }

                    }
                    Region newRegion = new Region();
                    // Split any large regions
                    for (int ys = 0; ys < region.rect.height; ys += tileHeight) {
                        for (int xs = 0; xs < region.rect.width; xs += tileWidth) {
                            newRegion = new Region();
                            newRegion.end = false;
                            if (xs != 0 || ys == 0) {
                                newRegion.start = false;
                            }
                            newRegion.name = region.name;// + "_" + Integer.toString(xs) + "_" + Integer.toString(ys);
                            newRegion.rect.x = region.rect.x + xs;
                            newRegion.rect.y = region.rect.y + ys;
                            newRegion.rect.width = tileWidth;
                            newRegion.rect.height = tileHeight;
                            newRegion.offsetX = xs + region.offsetX;
                            newRegion.offsetY = ys + region.offsetY;
                            newRegion.regionShift = region.regionShift;
                            newRegions.add(newRegion);
                        }
                    }
                    // Update the last output region
                    newRegion.end = true;
                } else {
                    // No region change, so just add it to the list
                    newRegions.add(region);
                }
            }

            regions = newRegions;
        }

        // If there aren't any regions then automatically create them assuming a regular tile grid
        if (regions == null || regions.isEmpty()) {
            regions = new ArrayList<>();

            for (int y = startY ; y < imageHeight ; y+=tileHeight) {
                for (int x = startX ; x < imageWidth ; x+=tileWidth) {
                    Region region = new Region();
                    region.name = Integer.toString(x) + "_" + Integer.toString(y);
                    region.rect.x = x;
                    region.rect.y = y;
                    region.rect.width = tileWidth;
                    region.rect.width = tileHeight;
                    regions.add(region);
                }
            }
        }
    }

    static int cumulativeSpriteCount = 0;
    static String lastGoodSpriteName;

    static int getOffsetForXY(int x , int y) {
        return (x/tileWidth) + ((y/tileHeight) * (imageWidth/tileHeight));
    }
    private static boolean processTileImageAt(boolean newSprite, int x, int y, Region region) {
        String tileScreenDebugName = "";
        if (fcdTileScreen != null && outputScreenData != null) {
            tileScreenDebugName = outputScreenData.replace(" " , "_").replace("-" , "_").replace("/" , "_").replace("\\" , "_").replace("." , "_").replace(":" , "_");
            tileScreenDebugName = "kTileScreenDebug_" + tileScreenDebugName + "_x" + x + "_y" + y + "_";
        }
        int dataOffset = getOffsetForXY(x,y);
        System.out.println(";Process x=" + x + " y=" + y + " offset=$" + Integer.toHexString(dataOffset));
        if (outputSprites != null) {
            outputSprites.println(";Process x=" + x + " y=" + y);
            if (newSprite) {
                outputSprites2.println("EmitSpriteFrame"+ nameSuffix + region.name);
                outputSprites2.println("\t+MEmitSpriteFrame_Preserve");

                lastGoodSpriteName = "kVarsEmitSpriteFrame"+ nameSuffix + region.name;
                outputSprites3.println("; New sprite: " + region.name);
                cumulativeSpriteCount = 0;

                newSprite = false;
            }
        }
        // First collect all unique colours used in the tile
        usedColours = new TreeSet<>();
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
                boolean ignorePalette = false;
                for (int ty = 0 ; ty < tileHeight && !ignorePalette ; ty++) {
                    for (int tx = 0 ; tx < tileWidth && !ignorePalette ; tx++) {
                        Integer colour = imageColours[x + tx + ((y + ty) * imageWidth)];
                        if (colour != null) {
                            if (forcedColourIndex.containsKey(colour) && palette.get(colour) != null) {
                                // If we are considering a forced index colour
                                if (forcedColourIndex.get(colour).equals(palette.get(colour))) {
                                    // ... and its loaded index precisely matches the forced index
                                    // Then it is a very good match, so skip it
                                    continue;
                                }
                                // Otherwise ignore the palette
                                ignorePalette = true;
                                break;
                            }


                            // Now try with the closest colour...
                            Integer closestColour = getBestPaletteColour(palette, colour);

                            if (forcedColourIndex.containsKey(colour) && palette.get(closestColour) != null) {
                                // If we are considering a forced index colour
                                if (forcedColourIndex.get(colour).equals(palette.get(closestColour))) {
                                    // ... and its loaded index precisely matches the forced index
                                    // Then it is a very good match, so skip it
                                    continue;
                                }
                                // Otherwise ignore the palette
                                ignorePalette = true;
                                break;
                            }

                            colourDifference += getColourDifference(new Color(closestColour) , new Color(colour));
                        }
                    }
                }

                // If we are ignore the palette then score it really badly, so it is used as a last resort, if at all
                if (ignorePalette) {
                    colourDifference = 256 * tileWidth * tileHeight * 2;
                }

                if (resultPalette == null || (!ignorePalette && !preferLaterPalettes && (colourDifference < bestDistance)) || (!ignorePalette && preferLaterPalettes && (colourDifference <= bestDistance))) {
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
        Integer coloursByIndex[] = new Integer[paletteMaxLen];
        Arrays.fill(coloursByIndex , 0);
        resultPalette.forEach((key, value) ->{
            if (forcedColourByIndex.containsKey(value)) {
                coloursByIndex[value] = forcedColourByIndex.get(value);
            } else {
                coloursByIndex[value] = key;
            }
        });


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
                        imageColoursResult.setRGB(x + tx , y + ty, coloursByIndex[value]);
                        if(value > 0) {
                            theTileHasContents = true;
                        }
                    } else {
                        // If it's transparent, then pick the transparent palette entry
                        imageColoursResult.setRGB(x + tx , y + ty, coloursByIndex[0]);
                    }
                }
            }
        }

        // The pattern of pixels to pull from the linear row/column tile data into the bitplane data
        int[] indexPick16_16 = {
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

        int[] indexPick8_8 = {
                0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
                0x08,0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,
                0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
                0x18,0x19,0x1a,0x1b,0x1c,0x1d,0x1e,0x1f,
                0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27,
                0x28,0x29,0x2a,0x2b,0x2c,0x2d,0x2e,0x2f,
                0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,
                0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x3e,0x3f
        };

        int[] indexPick = null;
        if (tileWidth == 8 && tileHeight == 8) {
            indexPick = indexPick8_8;
        } else if (tileWidth == 16 && tileHeight == 16) {
            indexPick = indexPick16_16;
        } else {
            // Other sizes just use a linear scan for now
            indexPick = new int[tileWidth*tileHeight];
            for (int i = 0 ; i < indexPick.length ; i++) {
                indexPick[i] = i;
            }
        }

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

        if (outputVectors != null) {
            for (int ys = 0 ; ys < tileHeight ; ys++) {
                if (vectorLeftLength > 0) {
                    vectorData.put((byte)vectorLeftPalette);
                    vectorData.put((byte)(vectorLeftLength - 2));
                }
                byte lastColour = theTile[ys * tileWidth];
                int lastPos = 0;
                for (int xs = 1 ; xs < tileWidth ; xs++) {
                    if ((lastColour != theTile[xs + (ys * tileWidth)]) || ((xs - lastPos) >= 254)) {
                        vectorData.put(lastColour);
                        vectorData.put((byte)((xs - lastPos) -2));

                        lastColour = theTile[xs + (ys * tileWidth)];
                        lastPos = xs;
                    }
                }
                // Output any remaining
                if ((tileWidth - lastPos) > 0) {
                    vectorData.put(lastColour);
                    vectorData.put((byte)((tileWidth - lastPos)-2));
                }

                if (vectorRightLength > 0) {
                    vectorData.put((byte)vectorRightPalette);
                    vectorData.put((byte)(vectorRightLength - 2));
                }
            }
        }

        if (outputTileBytes != null) {
            tileHasData = theTileHasContents;
        }

        int bestTileIndex = currentTile;
        boolean bestFlipX = false;
        boolean bestFlipY = false;
        int currentTileAddress = -1;
        if ((outputScreenData != null) || ((outputSprites != null) && tileHasData)) {
            String testTile = Base64.getEncoder().encodeToString(theTile);
            TileIndexFlip existingIndexFlip = tileToIndexFlip.get(testTile);

            if (existingIndexFlip != null) {
                bestFlipX = existingIndexFlip.flipX;
                bestFlipY = existingIndexFlip.flipY;
                bestTileIndex = existingIndexFlip.index;
                currentTileAddress = existingIndexFlip.address;
                System.out.println("Found existing tile: " + bestTileIndex + " ($" + Integer.toHexString(bestTileIndex)+ ")" + " " + (bestFlipX?"H":"") + (bestFlipY?"V":""));
                if (fcdTileScreen != null && outputScreenData != null) {
                    fcdTileScreen.println(tileScreenDebugName + "tile = " + bestTileIndex);
                    fcdTileScreen.println(tileScreenDebugName + "colour = " + bestFoundPaletteIndex);
                    fcdTileScreen.println(tileScreenDebugName + "reused = 1");
                }
            } else {
                currentTileAddress = tileByteData.position();
                // Advances the tile number, could do with duplicate check here
                tileByteData.put(theTile);
                if (outputPlanes != null) {
                    System.out.println("New tile needed: " + currentTile + " ($" + Integer.toHexString(currentTile) + ")" + "Bitplane remaining size " + bitplaneData[0].remaining());
                    for (int bp = 0; bp < numBitplanes; bp++) {
                        bitplaneData[bp].put(bitplaneDataTemp[bp]);
                    }
                    if (fcdTileScreen != null && outputScreenData != null) {
                        fcdTileScreen.println(tileScreenDebugName + "tile = " + currentTile);
                        fcdTileScreen.println(tileScreenDebugName + "colour = " + bestFoundPaletteIndex);
                        fcdTileScreen.println(tileScreenDebugName + "reused = 0");
                    }
                }

                existingIndexFlip = new TileIndexFlip();
                existingIndexFlip.flipX = false;
                existingIndexFlip.flipY = false;
                existingIndexFlip.index = currentTile;
                existingIndexFlip.address = currentTileAddress;
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
                existingIndexFlip.address = currentTileAddress;
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
                existingIndexFlip.address = currentTileAddress;
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
                existingIndexFlip.address = currentTileAddress;
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
            if (fcdTileScreen != null && outputScreenData != null) {
                fcdTileScreen.println(tileScreenDebugName + "flipx = 1");
            }
        } else {
            if (fcdTileScreen != null && outputScreenData != null) {
                fcdTileScreen.println(tileScreenDebugName + "flipx = 0");
            }
        }
        if (bestFlipY) {
            bestFoundPaletteIndex = bestFoundPaletteIndex | 0x80;
            // Mode7 tiles want the flip in screen data
            if (outputTileBytes != null) {
                bestTileIndex = bestTileIndex  | 0x80;
            }
            if (fcdTileScreen != null && outputScreenData != null) {
                fcdTileScreen.println(tileScreenDebugName + "flipy = 1");
            }
        } else {
            if (fcdTileScreen != null && outputScreenData != null) {
                fcdTileScreen.println(tileScreenDebugName + "flipy = 0");
            }
        }

        int theTileIndex = bestTileIndex;
        int theColour = (bestFoundPaletteIndex & (0x1f | 0x80 | 0x40));
        // Chars are handled, for their chosen palette range
        if (extraCharsBits) {
            theColour |= (bestTileIndex & 0x300) >> 4;
        }
        theTileIndex &= 0xff;
        theColour &= 0xff;
        if (outputScreenData != null) {
            screenTileData.put((byte)theTileIndex);
            screenColourData.put((byte)(theColour + paletteOffset));
        } else if (outputSprites != null) {
            if (tileHasData) {
                if (currentTile >= 24) {
                    outputSprites.print(";");
                }
                outputSprites.println("b" + theTileIndex + ",b" + (theColour + paletteOffset) + ",b" + spriteYPos + ",b" + spriteXPos);
                if (region.offsetX != 0 && region.offsetY != 0) {
                    outputSprites2.println("\t+MEmitSpriteFrameOffsetXY " + theTileIndex + " , " + (theColour + paletteOffset) + " , " + region.offsetX + " , " + region.offsetY);
                } else if (region.offsetX != 0) {
                    outputSprites2.println("\t+MEmitSpriteFrameOffsetX " + theTileIndex + " , " + (theColour + paletteOffset) + " , " + region.offsetX);
                } else if (region.offsetY != 0) {
                    outputSprites2.println("\t+MEmitSpriteFrameOffsetY " + theTileIndex + " , " + (theColour + paletteOffset) + " , " + region.offsetY);
                } else {
                    outputSprites2.println("\t+MEmitSpriteFrame " + theTileIndex + " , " + (theColour + paletteOffset));
                }
                if (outputScaled4 != null) {
                    outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileAddress=" + currentTileAddress);
                    outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileWidth=" + tileWidth);
                    labelToValue.add(new Pair<String,Integer>(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileWidth", tileWidth));
                    labelValues.add(tileWidth);
                    outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileHeight=" + tileHeight);
                    labelToValue.add(new Pair<String,Integer>(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileHeight", tileHeight));
                    labelValues.add(tileHeight);
                } else {
                    outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_tileIndex=" + theTileIndex);
                }

                outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_colour=" + (theColour + paletteOffset));
                outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_offsetX=" + region.offsetX);
                outputSprites3.println(lastGoodSpriteName + "_" + cumulativeSpriteCount+ "_offsetY=" + region.offsetY);
                cumulativeSpriteCount++;

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
        return newSprite;
    }

    private static Integer getBestPaletteIndex(HashMap<Integer, Integer> resultPalette, Integer colour) {
        if (forcedColourIndex.containsKey(colour)) {
            return forcedColourIndex.get(colour);
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

    private static void ImageScale(double scaleX , double scaleY) {
        System.out.println("Scale...");
        // Calculate the new image size
//        int dw = ((int) ((imageWidth+tileWidth-1) * scaleX)/tileWidth) * tileWidth;
//        int dh = ((int) ((imageHeight+tileHeight-1) * scaleY)/tileHeight) * tileHeight;
        int dw = ((int) (((imageWidth * scaleX)+tileWidth-1)/tileWidth)) * tileWidth;
        int dh = ((int) (((imageHeight * scaleY)+tileHeight-1)/tileHeight)) * tileHeight;

        Integer[] newImageColours = new Integer[dw*dh];
        imageColoursResultPre = new BufferedImage(dw , dh , BufferedImage.TYPE_INT_RGB);

        for (int y = 0 ; y < dh ; y++) {
            int dy = (int)(y/scaleY);
            dy = Math.min(dy, imageHeight-1);
            for (int x = 0 ; x < dw ; x++) {
                int dx = (int)(x/scaleX);
                dx = Math.min(dx, imageWidth-1);
                newImageColours[x + (y*dw)] = imageColours[dx + (dy * imageWidth)];
                imageColoursResultPre.setRGB(x,y,imageColours[dx + (dy * imageWidth)]);
            }
        }

        imageWidth = dw;
        imageHeight = dh;
        imageColours = newImageColours;
        imageColoursResult = new BufferedImage(dw , dh , BufferedImage.TYPE_INT_RGB);

        // Scale any regions that exist already, usually these are user input regions
        if (regions != null) {
            for (Region region : regions) {
                region.rect.x = (int) (region.rect.x * scaleX);
                region.rect.y = (int) (region.rect.y * scaleY);
                region.rect.width = (int) (region.rect.width * scaleX);
                region.rect.height = (int) (region.rect.height * scaleY);
                region.offsetX = (int) (region.offsetX * scaleX);
                region.offsetY = (int) (region.offsetY * scaleY);
            }
        }
    }

    private static void ImageQuantize() {
        System.out.println("Quantize...");

        ensureRegions();

        // Quantize tiles down to a maximum number of colours
        for (Region region : regions) {
            int x = region.rect.x;
            int y = region.rect.y;
            HashMap<Integer,Double> usedColours = new HashMap<>();
            for (Integer colour : forcedColourIndex.keySet()) {
                usedColours.put(colour , (double) (tileWidth*tileHeight)*10000);    // Significant weighting
            }
            for (int ty = 0 ; ty < tileHeight ; ty++) {
                for (int tx = 0 ; tx < tileWidth ; tx++) {
                    if ((tx + x) >= imageWidth || (ty + y) >= imageHeight) {
                        continue;
                    }

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
                System.out.println("Reduce x=" + x + " y=" + y + " has colours " + usedColours.size());
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
                        if ((tx + x) >= imageWidth || (ty + y) >= imageHeight) {
                            continue;
                        }

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
