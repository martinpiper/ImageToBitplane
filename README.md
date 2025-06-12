# ImageToBitplane
 Converts images to bitplanes which is useful for some types of graphics hardware.

* --rgbshift r g b
  * How many bits of colour to use for each component with a maximum of 8 bit RGB components.
  * e.g. RGB 444 use --rgbshift 4 4 4
  * e.g. RGB 565 use --rgbshift 5 6 5
  * e.g. RGB 888 use --rgbshift 8 8 8
  * Default is: RGB 444
* --newpalettes
  * Clears all palettes
* --resetforcergb
  * Clears the force colour list
* --forcergb r g b
  * Forces the RGB colour to be placed in the specific order in all palettes that are to be created. Useful for defining strict palette ordering and the transparent colour.
* --rgbfactor r g b factor
  * during quantization, the RGB colour is weighted by the multiplier factor
* --resetrgbfactor
  * Clears the RGB factor list
* --paletteaddrgbs
  * Adds RGB triplets separated by spaces (e.g. 255 128 64) until the next command line option starting with "--" to one palette. Any current force RGB colours are added first.
* --paletteoffset offset
  * Adds a palette number value when writing data. Useful for adding an offset to sprite palette index structures or converting screens.
* --palettesize size
  * Specifics the number of entries in a palette. Usually 16.
* --minpalettesize size
  * The minimum size for a palette entry. Usually 16.
* --loadpaletteraw filename
  * Loads colours in raw mode, basically loading each colour and filling palettes without any duplicate check.
* --loadpalette filename
  * Loads colours with duplicate colour checks for the currently active palette.
* --loadpalettebestfit filename
  * Loads colours with duplicate colours checks across all palettes.
* --onlyloadwholepalettes filename
  * This only loads whole palette entries and avoids creating partially filled palettes. This means --loadpalettebestfit should be used later, when needed, to ensure any remaining colours are loaded.   
* --spritexy x y
  * When writing sprite data structures, this is the starting output x/y position
* --imagescale x y
  * Scales the image with x/y values.
* --imagequantize count
  * Quantizes the loaded image down, or individual tiles or regions if used after --tilewh or --region, to the number of colours.
* --tilewh w h
  * Sets the output tile/sprite size
* --startxy x y
  * The start x/y coordinates to use for image conversion
* --imagewh width height
  * Set new image width and height values after an image has been given.
* --image filename
  * The input image filename to use for conversion.
* --numbitplanes count
  * The number of bit planes to output.
* --nowrite
  * Reset any configures files, no conversion.
* --nowritepass
  * Process the data internally, but don't write any files and forget any file output configuration. Useful for accumulating palette data.
* --outputvectors leftLength leftPalette rightLength rightPalette filename
  * Process the image as a vector screen, using the left/right length (border) and palette information.
* --outputplanes filename
  * Output bitplane data to the file.
* --outputscaled filename
  * Output scaled sprite data to the file.
* --outputtilebytes filename
  * Output mode7 tiles data to the file.
* --outputscrcol filename
  * Output tile screen and colour data to the file.
* --outputsprites filename
  * Output sprite structure data to the file.
* --outputpalettes filename
  * Output palette data to the file.
* --convertwritepass
  * Conversion pass and data output.
* --convertpass
  * Just a conversion pass.
* --usestacking
  * Process sprites with stacking, so any image data that does not fit within the current palette and sprite will then use a new sprite.
* --nostacking
  * Disable sprite stacking.
* --palettequantize count
  * Quantizes the current palettes to the specified number.
* --fitpalettes
  * Enables image processing where the current palettes are used, with best colour matching, instead of adding new colours to palettes.
  * Generally this means images doing not need to be quantized since the palettes have already been chosen.
* --splitmaps
  * Splits output map data, for screen and colours, into separate files.
* --nosplitmaps
  * Disables split maps
* --chars
  * Enables extra character index values to be output to the colour map data.
* --nochars
  * Disables chars mode.
* --concat filename1 filename2 filenameOutput
  * Vertically concatenates the two images into the third image file.
* --concath filename1 filename2 filenameOutput
  * Horizontally concatenates the two images into the third image file.
* --planestoscaled filename1 filename2 filename3 filename4 filenameOutput
  * Converts sprite bitplane data to scaled sprite data format
* --region name hotX hotY offsetX offsetY width height
  * For sprite sheet conversion, creates a named sheet, starting from hot point X/Y in the image, applies the start X/Y offset and width/height as the conversion rectangle.
* --regionxy name hotX hotY left top right bottom
  * For sprite sheet conversion, creates a named sheet, with the hot point X/Y and bounding rectangle X/Y.
* --regionshift
  * For regions, the image data is shifted to reduce as much as possible any wasted space.
* --noregionshift
  * Disables region shift.
* --namesuffix name
  * For sprites, adds a name suffix to structures.
* --nonamesuffix
  * Removes any name suffix.
* --removeregions
  * Removes any previous regions, allowing a previous image to be reused with different regions.
* --preservedata
  * Preserves char, colour, tile data from a previous conversion pass.
* --nopreservedata
  * Does not preserve previous data.
* Image processing options
  * --transparentRGB 255 0 255
    * For image processing (--processNow) sets the transparent colour
  * --shiftTopLeft
    * Shifts the image to the top left considering transparent pixels
  * --minimiseArea
    * Reduces wasted space to the right and bottom considering transparent pixels
  * --processNow
    * Processes image filenames with the above options
  * --removeDuplicates
    * Removes identically coloured images, includes X and Y flips
