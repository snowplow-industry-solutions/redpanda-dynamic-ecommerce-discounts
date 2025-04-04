#!/usr/bin/awk -f

BEGIN { FS="msg=" }
{
    match($0, /msg="([^"]*)"/, arr);
    if (arr[1] != "") {
        gsub(/\\n/, "\n", arr[1]);
        gsub(/\\\n/, "\n", arr[1]);
        print arr[1];
    }
    fflush(stdout);
}
