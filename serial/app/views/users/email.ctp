<fieldset>
<legend><h1>EMAIL -- SMTP</h1></legend>
<?php
	echo $form->create('User', array('action' => 'http'));
	echo $form->input('name', array('label'=>'LoginName:')); 
	echo $form->input('passwd', array('label'=>'Password:', 'type' => 'password')); 
	echo $form->input('to', array('label'=>'MailTo:')); 
	echo $form->input('to', array('label'=>'MailHost:'));
	echo $form->input('from', array('label'=>'MailFrom:')); 
	echo $form->input('title', array('label'=>'MailTitle:')); 
	echo $form->input('content', array('label'=>'MailContent:')); 
?>
</fieldset>
<?php echo $form->end('Send!'); ?>