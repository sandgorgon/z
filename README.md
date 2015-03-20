z
=

[Plan 9](http://plan9.bell-labs.com/plan9/) Acme Inspired Editor, done in Scala.

Used Scala 2.11.6 and Java 1.7.0_67

To build it, go to the root of the source and then,

	cd bin
	scalac ../*.scala
	cd ..
	jar cmf MANIFEST.MF dist/z.jar -C bin .

	... and you can just copy the files in dist and put it anywhere you want. For example for me, I put it in $HOME/z .

The only 'clean up' I do is just remove all the .class files in bin, and that's it.

The bash script I use to run the editor,

	#!/bin/bash
	export Z_PATH=$HOME/z
	exec scala $Z_PATH/z.jar $*

How to use it is documented in the [Z Help Screen](https://github.com/sandgorgon/z/blob/master/bin/help/main.txt).

