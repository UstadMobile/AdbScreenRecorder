# As per https://android.developreference.com/article/18152336/Use+adb+screenrecord+command+to+mirror+Android+screen+to+PC+via+USB
adb shell screenrecord --bit-rate=16m --output-format=h264 --size 800x600 - | ffplay -framerate 60 -framedrop -bufsize 16M -
