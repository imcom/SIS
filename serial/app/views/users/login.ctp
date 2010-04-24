<fieldset>
<legend><?php printf(__('   用户%s', true), __('登录', true)); ?></legend>
<?php $session->flash('auth');?>
<?php echo $form->create('User', array('action' => 'login')); ?>
<?php
echo $form->input('username', array(
'label'=>'用户名'
)
);
echo $form->input('password', array( 
'type' => 'password', 
'label'=>'密码'
)
);
?>
</fieldset>
<?php echo $form->end('登录');?>
<?php echo $html->link('立即注册', array('action' => 'register')); ?>