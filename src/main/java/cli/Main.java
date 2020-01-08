package cli;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) throws Exception {
        String path = "src/test/resources/TestImage1.png";
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

        int numBitplanes = 3;
        ByteBuffer[] bitplaneData = new ByteBuffer[numBitplanes];

        int paletteMaxLen = 8;
        HashMap<Color , Integer> forcedColourIndex = new HashMap<>();
        forcedColourIndex.put(new Color(255, 0, 255) , forcedColourIndex.size());

        int tileWidth = 16 , tileHeight = 16;
        int startX = 0 , startY = 0;

        for (int y = startY ; y < imageHeight ; y+=tileHeight) {
            for (int x = startX ; x < imageWidth ; x+=tileWidth) {
                // First collect all unique colours used in the tile
                HashSet<Color> usedColours = new HashSet<>();
                for (int ty = 0 ; ty < tileHeight ; ty++) {
                    for (int tx = 0 ; tx < tileWidth ; tx++) {
                        usedColours.add(imageColours[x + tx + ((y + ty) * imageWidth)]);
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
                    if (!palette.containsKey(colour)) {
                        palette.put(colour, palette.size());
                    }
                }

                assert palette.size() < paletteMaxLen;

                // Now convert the tile to bitplane data based on the colours in the palette and the index values
            }
        }

        System.out.println("Foo");
    }
}
