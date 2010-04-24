<h1>HTTP Response</h1>
<?php
$fp = @fopen('D:/xampp/htdocs/serial/'.$_GET['tar'], 'r');
echo fread($fp, filesize('D:/xampp/htdocs/serial/'.$_GET['tar']));
fclose($fp);
//unlink('D:/xampp/htdocs/serial/'.$_GET['tar']);
?>