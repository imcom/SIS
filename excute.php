<?php

/*****************************************************************
* PHP sends some headers by default. Stop them.
******************************************************************/

// Clear the default mime-type
header('Content-Type:');

// And remove the caching headers
header('Cache-Control:');
header('Last-Modified:');

$user_IP = ($_SERVER["HTTP_VIA"]) ? $_SERVER["HTTP_X_FORWARDED_FOR"] : $_SERVER["REMOTE_ADDR"];
$user_IP = ($user_IP) ? $user_IP : $_SERVER["REMOTE_ADDR"];
$uid = md5($user_IP);

require 'includes/init.php';

require_once("./lajp/php/php_java.php");

require GLYPE_ROOT . '/includes/parser.php';

$parser = new parser(null, null);

switch ( true ) {

   // Try query string for URL
   case ! empty($_GET['u']) && ( $toLoad = deproxifyURL($_GET['u'], true) ):
      break;
      
   // Try path info
   case ! empty($_SERVER['PATH_INFO'])  && ( $toLoad = deproxifyURL($_SERVER['PATH_INFO'], true) ):
      break;
      
   // Found no valid URL, return to index
   default:
      redirect();
}

if ( ! preg_match('#^((https?)://(?:([a-z0-9-.]+:[a-z0-9-.]+)@)?([a-z0-9-.]+)(?::([0-9]+))?)(?:/|$)((?:[^?/]*/)*)([^?]*)(?:\?([^\#]*))?(?:\#.*)?$#i', $toLoad, $tmp) ) {

   // Invalid, show error
   error('invalid_url', $toLoad);

}

// Rename parts to more useful names
$URL  =  array('scheme_host' => $tmp[1],
               'scheme'      => $tmp[2],
               'auth'        => $tmp[3],
               'host'        => $tmp[4],
               'domain'      => preg_match('#(?:^|\.)([a-z0-9-]+\.(?:[a-z.]{5,6}|[a-z]{2,}))$#', $tmp[4], $domain) ? $domain[1] : $tmp[4], // Attempt to split off the subdomain (if any)
               'port'        => $tmp[5],
               'path'        => '/' . $tmp[6],
               'filename'    => $tmp[7],
               'extension'   => pathinfo($tmp[7], PATHINFO_EXTENSION),
               'query'       => isset($tmp[8]) ? $tmp[8] : '');

// Apply encoding on full URL. In theory all parts of the URL need various special
// characters encoding but this needs to be done by the author of the webpage.
// We can make a guess at what needs encoding but some servers will complain when
// receiving the encoded character instead of unencoded and vice versa. We want
// to edit the URL as little as possible so we're only encoding spaces, as this
// seems to 'fix' the majority of cases.
$URL['href'] = str_replace(' ', '%20', $toLoad);


/*****************************************************************
* Post
* Forward the post data. Usually very simple but complicated by
* multipart forms because in those cases, the raw post is not available.
******************************************************************/

if ( ! empty($_POST) ) {

   // Attempt to get raw POST from the input wrapper
   if ( ! ($tmp = file_get_contents('php://input')) ) {

      // Raw data not available (probably multipart/form-data).
      // cURL will do a multipart post if we pass an array as the
      // POSTFIELDS value but this array can only be one deep.

      // Recursively flatten array to one level deep and rename keys
      // as firstLayer[second][etc]. Also apply the input decode to all
      // array keys.
      function flattenArray($array, $prefix='') {

         // Start with empty array
         $stack = array();

         // Loop through the array to flatten
         foreach ( $array as $key => $value ) {

            // Decode the input name
            $key = inputDecode($key);

            // Determine what the new key should be - add the current key to
            // the prefix and surround in []
            $newKey = $prefix ? $prefix . '[' . $key . ']' : $key;

            if ( is_array($value) ) {

               // If it's an array, recurse and merge the returned array
               $stack = array_merge($stack, flattenArray($value, $newKey));

            } else {

               // Otherwise just add it to the current stack
               $stack[$newKey] = clean($value);

            }

         }

         // Return flattened
         return $stack;

      }

      $tmp = flattenArray($_POST);

      // Add any file uploads?
      if ( ! empty($_FILES) ) {

         // Loop through and add the files
         foreach ( $_FILES as $name => $file ) {

            // Is this an array?
            if ( is_array($file['tmp_name']) ) {

               // Flatten it - file arrays are in the slightly odd format of
               // $_FILES['layer1']['tmp_name']['layer2']['layer3,etc.'] so add
               // layer1 onto the start.
               $flattened = flattenArray(array($name => $file['tmp_name']));

               // And add all files to the post
               foreach ( $flattened as $key => $value ) {
                  $tmp[$key] = '@' . $value;
               }

            } else {

               // Not another array. Check if the file uploaded successfully?
               if ( ! empty($file['error']) || empty($file['tmp_name']) ) {
                  continue;
               }

               // Add to array with @ - tells cURL to upload this file
               $tmp[$name] = '@' . $file['tmp_name'];

            }

            // To do: rename the temp file to it's real name before
            // uploading it to the target? Otherwise, the target receives
            // the temp name instead of the original desired name
            // but doing this may be a security risk.

         }

      }

      }

   // Convert back to GET if required
	if ( isset($_POST['convertGET']) ) {

		// Remove convertGET from POST array and update our location
		$URL['href'] .= ( empty($URL['query']) ? '?' : '&' ) . str_replace('convertGET=1', '', $tmp);

	} else {

		// Genuine POST so set the cURL post value
		$toSet[CURLOPT_POST] = 1;
		$toSet[CURLOPT_POSTFIELDS] = $tmp;

	}

}

//print_r($URL);

$request = "#HREQ#".$uid.$toLoad."#END#";

try{
	$ret = lajp_call('MyController.SocketInvoker::request', $request);
	//$ret = lajp_call('lajp.socketor::request', $toLoad);
}catch(Exception $e){
	echo "Err:{$ret}<br>";
}

list($res, $type, $other) = explode("##", $ret);

//var_dump($type);
switch($type){
	case "text/html":
	
		if ( $flag != 'frame' && $fetch->sniff == false ) {
			foreach ( $CONFIG['options'] as $name => $details ) {
				if ( ! empty($details['force']) )  {
					continue;
				}
				$toShow[] = array(
					'name'     => $name,
					'title'    => $details['title'],
					'checked'  => $options[$name] ? ' checked="checked" ' : ''
				);
			}

			// Prepare variables to pass to template
			$vars['toShow']   = $toShow;                    // Options
			$vars['url']      = $URL['href'];               // Currently visited URL
			$vars['return']   = rawurlencode(currentURL()); // Return URL (for clearcookies) (i.e. current URL proxified)
			$vars['proxy']    = GLYPE_URL;                  // Base URL for proxy directory

			// Load the template
			$insert = loadTemplate('framedForm.inc', $vars);
			if ( $CONFIG['override_javascript'] ) {
				$insert = '<script type="text/javascript">disableOverride();</script>'
				. $insert
				. '<script type="text/javascript">enableOverride();</script>';
			}
		
			// And load the footer
			$footer = $CONFIG['footer_include'];

			// Inject javascript unless sniffed
			if ( $fetch->sniff == false ) {
				$inject = true;
			}
		}
	
		$res = $parser->HTMLDocument($res, $insert, $inject, $footer);
		header("content-type:text/html;charset=gbk");
		$res = mb_convert_encoding($res, "gbk", "utf-8");
		echo $res;
		break;
	case "text/css":
		$res = $parser->CSS($res);
		header("content-type:text/css");
		echo $res;
		break;
	case "text/javascript":
		header("content-type:text/javascript");
		$res = $parser->JS($res);
		break;
	case "application/x-javascript":
		header("content-type:text/javascript");
		$res = $parser->JS($res);
		break;
	case "image/gif":
		$res = base64_decode($res);
		$im = imagecreatefromstring($res);
		if ($im !== false) {
			header("content-type:image/gif");
			imagegif($im);
			imagedestroy($im);
		}
		echo $res;
		break;
	case "image/png":
		$res = base64_decode($res);
		$im = imagecreatefromstring($res);
		if ($im !== false) {
			header("content-type:image/png");
			imagegif($im);
			imagedestroy($im);
		}
		echo $res;
		break;
	case "image/jpeg":
		$res = base64_decode($res);
		$im = imagecreatefromstring($res);
		if ($im !== false) {
			header("content-type:image/jpeg");
			imagegif($im);
			imagedestroy($im);
		}
		echo $res;
		break;
	default:
		break;
}

?>