<fieldset>
<legend>欢迎使用网络隔离系统</legend>
<?php echo $html->link('HTTP 访问', array('controller'=>'users', 'action' => 'http'))?>
</br>
<?php echo $html->link('Email 服务', array('controller'=>'users', 'action' => 'email'))?>
</fieldset>