# ImageToBitplane
 Converts images to bitplanes which is useful for some types of graphics hardware.

* --rgbshift r g b
  * How many bits of colour to use for each component with a maximum of 8 bit RGB components.
  * e.g. RGB 444 use --rgbshift 4 4 4
  * e.g. RGB 565 use --rgbshift 5 6 5
  * e.g. RGB 888 use --rgbshift 8 8 8
  * Default is: RGB 444
