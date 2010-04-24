<?php
class UsersController extends AppController {
	var $name = 'Users';
	var $components = Array('Auth');
	var $helpers = array('Html', 'Form');
	
	function beforeFilter(){
		Security::setHash('sha256');
		$this->Auth->userModel = 'User';
		$this->Auth->allowedActions = array('register');
		$this->Auth->LoginRedirect = array('controller'=>'pages', 'action'=>'index');
		$this->Auth->autoRedirect = false;
	}
	
	function http(){
		$path = dirname(__FILE__);
		require_once($path.'/lajp/php/php_java.php');
		if(isset($this->data)){
			$request = "#HREQ#".md5($this->Auth->user('id')).$this->data['User']['url']."#END#";
			echo $request;
			try{
				$ret = lajp_call('MyController.SocketInvoker::request', $request);
			}catch(Exception $e){
				echo "Err:{$ret}<br>";
			}
			$fp = @fopen('D:/xampp/htdocs/serial/'.md5($this->Auth->user('id')), 'w');
			fwrite($fp, $ret);
			fclose($fp);
			$this->redirect(array('action'=>'view?tar='.md5($this->Auth->user('id'))));
		}
	}
	
	function view(){
		
	}
	
	function email(){
		$path = dirname(__FILE__);
		require_once($path.'/lajp/php/php_java.php');
		if(isset($this->data)){
			$request = "#HREQ#".md5($this->Auth->user('id'))."#".$this->data['User']['to']
			."#".$this->data['User']['from']."#".$this->data['User']['user']."#".$this->data['User']['passwd']
			."#".$this->data['User']['host']."#".$this->data['User']['content']."#".$this->data['User']['title']."#END#";
			echo $request;
			try{
				$ret = lajp_call('MyController.EmailInvoker::request', $request);
			}catch(Exception $e){
				echo "Err:{$ret}<br>";
			}
			$this->Session->setFlash($ret);
		}
	}
	
	function register(){
		if($this->data){
			if($this->data['User']['username'] == ''){
				$this->Session->setFlash("请填写用户名！");
				$this->redirect('/users/register');
				exit();
			}
			if($this->User->findByUsername($this->data['User']['username']) > 0){
				$this->Session->setFlash("该名称已经注册，请更换一个！");
				$this->redirect('/users/register');
				exit();
			}
			if($this->data['User']['password_confirm'] == '' || $this->data['User']['password'] == ''){
				$this->Session->setFlash("请填写密码！");
				$this->redirect('/users/register');
				exit();
			}
			if ($this->data['User']['password'] == $this->Auth->password($this->data['User']['password_confirm'])){
				$this->User->create();
				if($this->User->save($this->data)){
					$this->Auth->login($this->data);
					$this->redirect('/pages/index');
				}else{
					$this->Session->setFlash("注册时发生错误，请重试！");
					$this->redirect('/users/register');
				}
			}else{
				$this->Session->setFlash("两次输入密码不一致，请重试！");
				$this->redirect('/users/register');
			}
		}
	}
	
	function login(){
		if($this->Auth->user())
			$this->redirect('/pages/index');
	}
	
	function logout(){
		$this->redirect($this->Auth->logout());
	}
}
?>