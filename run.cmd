echo off

SET VERSION=%1
SET MAINCLASS=""

if "%VERSION%" == "1" (
    SET MAINCLASS="jwj.first.App"
)

if "%VERSION%" == "2" (
    SET MAINCLASS="jwj.second.App"
)

if "%VERSION%" == "3" (
    SET MAINCLASS="jwj.third.App"
)


REM Start three nodes in our cluster
start "APP_ONE" cmd /k java -cp build\libs\etcd-cats-talk-1.0.jar %MAINCLASS% APP_ONE
start "APP_TWO" cmd /k java -cp build\libs\etcd-cats-talk-1.0.jar %MAINCLASS% APP_TWO
start "APP_THREE" cmd /k java -cp build\libs\etcd-cats-talk-1.0.jar %MAINCLASS% APP_THREE