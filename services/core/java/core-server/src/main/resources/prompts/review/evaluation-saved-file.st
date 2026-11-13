- "<filename>" (<if(contentType)><contentType><else>type unknown<endif>, <size> bytes)
<if(preview)>
Its opening lines<if(truncated)> (the file continues past them)<endif>:
----- start of "<filename>" -----
<preview>
----- end of "<filename>" -----
<elseif(binary)>
Binary, so its content is not shown.
<elseif(empty)>
The file is empty.
<else>
Its content is not shown: the files before it used up the preview space.
<endif>
