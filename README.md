z
=

[Plan 9](http://plan9.bell-labs.com/plan9/) Acme Inspired Editor, done in Scala.

The [Downloads](https://github.com/sandgorgon/z/downloads) section has distribution bundles built using the latest code and Scala 2.9.2 and Java 1.6.

In Ubuntu, here is a script I use to run the editor using the download bundle.

	#!/bin/sh
	Z_PATH=$HOME/z
	java -cp $Z_PATH -jar $Z_PATH/z.jar $* 

How to use it is documented in the [Z Help Screen](https://github.com/sandgorgon/z/blob/master/bin/help/main.txt).

