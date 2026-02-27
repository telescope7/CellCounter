#!/usr/bin/env bash

/Library/Java/JavaVirtualMachines/temurin-23.jdk/Contents/Home/bin/java \
-Djava.library.path=/usr/local/opencv/share/java/opencv4 \
-Dfile.encoding=UTF-8 \
-Dstdout.encoding=UTF-8 \
-Dstderr.encoding=UTF-8 \
-classpath /Users/mthomas/eclipse-workspace/CellCounter/bin:/usr/local/opencv/share/java/opencv4/opencv-4120.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/hamcrest-core-1.3.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/jcommon-1.0.23.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/jfreechart-1.0.19.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/jfreechart-1.0.19-experimental.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/jfreechart-1.0.19-swt.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/jfreesvg-2.0.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/junit-4.11.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/orsoncharts-1.4-eval-nofx.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/orsonpdf-1.6-eval.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/servlet.jar:/Users/mthomas/eclipse-workspace/CellCounter/lib/swtgraphics2d.jar com.prolymphname.cellcounter.CellCounterApp "$@"
