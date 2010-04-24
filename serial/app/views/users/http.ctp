<fieldset>
<legend><h1>HTTP</h1></legend>
<?php
	echo $form->create('User', array('action' => 'http'));
	echo $form->input('url', array('label'=>'URL:')); 
?>
</fieldset>
<?php echo $form->end('Go!'); ?>