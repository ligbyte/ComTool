package com.lime.ComTool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.lime.bean.AssistBean;
import com.lime.bean.ComBean;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.text.method.KeyListener;
import android.text.method.NumberKeyListener;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android_serialport_api.SerialPortFinder;


public class ComToolActivity extends Activity {
	EditText editTextRecDisp,editTextCOMA,editTextCOMB,editTextCOMC,editTextCOMD;
	EditText editTextTimeCOMA,editTextTimeCOMB,editTextTimeCOMC,editTextTimeCOMD;
	CheckBox checkBoxAutoClear,checkBoxAutoCOMA,checkBoxAutoCOMB,checkBoxAutoCOMC,checkBoxAutoCOMD;
	Button ButtonClear,ButtonSendCOMA,ButtonSendCOMB,ButtonSendCOMC,ButtonSendCOMD;
	ToggleButton toggleButtonCOMA,toggleButtonCOMB,toggleButtonCOMC,toggleButtonCOMD;
	Spinner SpinnerCOMA,SpinnerCOMB,SpinnerCOMC,SpinnerCOMD;
	Spinner SpinnerBaudRateCOMA,SpinnerBaudRateCOMB,SpinnerBaudRateCOMC,SpinnerBaudRateCOMD;
	RadioButton radioButtonTxt,radioButtonHex;
	SerialControl ComA,ComB,ComC,ComD;//4个串口
	DispQueueThread DispQueue;//刷新显示线程
	SerialPortFinder mSerialPortFinder;//串口设备搜索
	AssistBean AssistData;//用于界面数据序列化和反序列化
	int iRecLines=0;//接收区行数
	boolean showMillis=false;
	boolean showTx=false;
	boolean showMulti=false;
	boolean showSaveLog=false;
	CheckBox dialogSaveCheck;
	ExecutorService logWriter;
	static final String PREF_SETTINGS="ComToolSettings";
	static final String KEY_MILLIS="show_millis";
	static final String KEY_TX="show_tx";
	static final String KEY_MULTI="show_multi";
	static final String KEY_SAVE="save_log";
	static final int REQ_STORAGE=1001;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ComA = new SerialControl();
        ComB = new SerialControl();
        ComC = new SerialControl();
        ComD = new SerialControl();
        DispQueue = new DispQueueThread();
        DispQueue.start();
        AssistData = getAssistData();
        logWriter = Executors.newSingleThreadExecutor();
        loadSettings();
        setControls();
        bindSendLogger();
    }
    @Override
    public void onDestroy(){
    	saveAssistData(AssistData);
    	CloseComPort(ComA);
    	CloseComPort(ComB);
    	CloseComPort(ComC);
    	CloseComPort(ComD);
    	if (logWriter != null)
		{
			logWriter.shutdown();
		}
    	super.onDestroy();
    }
    @Override
    protected void onResume() {
    	super.onResume();
    	if (getSettings().getBoolean(KEY_SAVE, false) && hasStoragePermission())
		{
			showSaveLog = true;
			if (dialogSaveCheck != null)
			{
				dialogSaveCheck.setChecked(true);
			}
		}
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      CloseComPort(ComA);
	  CloseComPort(ComB);
	  CloseComPort(ComC);
	  CloseComPort(ComD);
      setContentView(R.layout.main);
      loadSettings();
      setControls();
      bindSendLogger();
    }
    
    //----------------------------------------------------
    private void setControls()
	{
    	String appName = getString(R.string.app_name);
        try {
			PackageInfo pinfo = getPackageManager().getPackageInfo("com.lime.ComTool", PackageManager.GET_CONFIGURATIONS);
			String versionName = pinfo.versionName;
//			String versionCode = String.valueOf(pinfo.versionCode);
			setTitle(appName+" V"+versionName);
        } catch (NameNotFoundException e) {
        	e.printStackTrace();
        }
    	editTextRecDisp=(EditText)findViewById(R.id.editTextRecDisp);
    	editTextCOMA=(EditText)findViewById(R.id.editTextCOMA);
    	editTextCOMB=(EditText)findViewById(R.id.editTextCOMB);
    	editTextCOMC=(EditText)findViewById(R.id.editTextCOMC);
    	editTextCOMD=(EditText)findViewById(R.id.editTextCOMD);
    	editTextTimeCOMA = (EditText)findViewById(R.id.editTextTimeCOMA);
		editTextTimeCOMB= (EditText)findViewById(R.id.editTextTimeCOMB);
		editTextTimeCOMC= (EditText)findViewById(R.id.editTextTimeCOMC);
		editTextTimeCOMD= (EditText)findViewById(R.id.editTextTimeCOMD);
    	
    	checkBoxAutoClear=(CheckBox)findViewById(R.id.checkBoxAutoClear);
		checkBoxAutoCOMA=(CheckBox)findViewById(R.id.checkBoxAutoCOMA);
		checkBoxAutoCOMB=(CheckBox)findViewById(R.id.checkBoxAutoCOMB);
		checkBoxAutoCOMC=(CheckBox)findViewById(R.id.checkBoxAutoCOMC);
		checkBoxAutoCOMD=(CheckBox)findViewById(R.id.checkBoxAutoCOMD);
    	ButtonClear=(Button)findViewById(R.id.ButtonClear);
    	ButtonSendCOMA=(Button)findViewById(R.id.ButtonSendCOMA);
    	ButtonSendCOMB=(Button)findViewById(R.id.ButtonSendCOMB);
    	ButtonSendCOMC=(Button)findViewById(R.id.ButtonSendCOMC);
    	ButtonSendCOMD=(Button)findViewById(R.id.ButtonSendCOMD);
    	toggleButtonCOMA=(ToggleButton)findViewById(R.id.toggleButtonCOMA);
    	toggleButtonCOMB=(ToggleButton)findViewById(R.id.ToggleButtonCOMB);
    	toggleButtonCOMC=(ToggleButton)findViewById(R.id.ToggleButtonCOMC);
    	toggleButtonCOMD=(ToggleButton)findViewById(R.id.ToggleButtonCOMD);
    	SpinnerCOMA=(Spinner)findViewById(R.id.SpinnerCOMA);
    	SpinnerCOMB=(Spinner)findViewById(R.id.SpinnerCOMB);
    	SpinnerCOMC=(Spinner)findViewById(R.id.SpinnerCOMC);
    	SpinnerCOMD=(Spinner)findViewById(R.id.SpinnerCOMD);
    	SpinnerBaudRateCOMA=(Spinner)findViewById(R.id.SpinnerBaudRateCOMA);
    	SpinnerBaudRateCOMB=(Spinner)findViewById(R.id.SpinnerBaudRateCOMB);
    	SpinnerBaudRateCOMC=(Spinner)findViewById(R.id.SpinnerBaudRateCOMC);
    	SpinnerBaudRateCOMD=(Spinner)findViewById(R.id.SpinnerBaudRateCOMD);
    	radioButtonTxt=(RadioButton)findViewById(R.id.radioButtonTxt);
    	radioButtonHex=(RadioButton)findViewById(R.id.radioButtonHex);
    	
    	editTextCOMA.setOnEditorActionListener(new EditorActionEvent());
		editTextCOMB.setOnEditorActionListener(new EditorActionEvent());
		editTextCOMC.setOnEditorActionListener(new EditorActionEvent());
		editTextCOMD.setOnEditorActionListener(new EditorActionEvent());
		editTextTimeCOMA.setOnEditorActionListener(new EditorActionEvent());
		editTextTimeCOMB.setOnEditorActionListener(new EditorActionEvent());
		editTextTimeCOMC.setOnEditorActionListener(new EditorActionEvent());
		editTextTimeCOMD.setOnEditorActionListener(new EditorActionEvent());
		editTextCOMA.setOnFocusChangeListener(new FocusChangeEvent());
		editTextCOMB.setOnFocusChangeListener(new FocusChangeEvent());
		editTextCOMC.setOnFocusChangeListener(new FocusChangeEvent());
		editTextCOMD.setOnFocusChangeListener(new FocusChangeEvent());
		editTextTimeCOMA.setOnFocusChangeListener(new FocusChangeEvent());
		editTextTimeCOMB.setOnFocusChangeListener(new FocusChangeEvent());
		editTextTimeCOMC.setOnFocusChangeListener(new FocusChangeEvent());
		editTextTimeCOMD.setOnFocusChangeListener(new FocusChangeEvent());

    	radioButtonTxt.setOnClickListener(new radioButtonClickEvent());
    	radioButtonHex.setOnClickListener(new radioButtonClickEvent());
    	ButtonClear.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMA.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMB.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMC.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMD.setOnClickListener(new ButtonClickEvent());
    	toggleButtonCOMA.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMB.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMC.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMD.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	checkBoxAutoCOMA.setOnCheckedChangeListener(new CheckBoxChangeEvent());
    	checkBoxAutoCOMB.setOnCheckedChangeListener(new CheckBoxChangeEvent());
    	checkBoxAutoCOMC.setOnCheckedChangeListener(new CheckBoxChangeEvent());
    	checkBoxAutoCOMD.setOnCheckedChangeListener(new CheckBoxChangeEvent());
    	
    	ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, 
    			R.array.baudrates_value, R.layout.spinner_item);
    	adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    	SpinnerBaudRateCOMA.setAdapter(adapter);
    	SpinnerBaudRateCOMB.setAdapter(adapter);
    	SpinnerBaudRateCOMC.setAdapter(adapter);
    	SpinnerBaudRateCOMD.setAdapter(adapter);
    	SpinnerBaudRateCOMA.setSelection(12);
    	SpinnerBaudRateCOMB.setSelection(12);
    	SpinnerBaudRateCOMC.setSelection(12);
    	SpinnerBaudRateCOMD.setSelection(12);
    	
    	mSerialPortFinder= new SerialPortFinder();
    	String[] entryValues = mSerialPortFinder.getAllDevicesPath();
    	List<String> allDevices = new ArrayList<String>();
		for (int i = 0; i < entryValues.length; i++) {
			allDevices.add(entryValues[i]);
		}
		ArrayAdapter<String> aspnDevices = new ArrayAdapter<String>(this,
				R.layout.spinner_item, allDevices);
		aspnDevices.setDropDownViewResource(R.layout.spinner_dropdown_item);
		SpinnerCOMA.setAdapter(aspnDevices);
		SpinnerCOMB.setAdapter(aspnDevices);
		SpinnerCOMC.setAdapter(aspnDevices);
		SpinnerCOMD.setAdapter(aspnDevices);
		if (allDevices.size()>0)
		{
			SpinnerCOMA.setSelection(0);
		}
		if (allDevices.size()>1)
		{
			SpinnerCOMB.setSelection(1);
		}
		if (allDevices.size()>2)
		{
			SpinnerCOMC.setSelection(2);
		}
		if (allDevices.size()>3)
		{
			SpinnerCOMD.setSelection(3);
		}
		SpinnerCOMA.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerCOMB.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerCOMC.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerCOMD.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerBaudRateCOMA.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerBaudRateCOMB.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerBaudRateCOMC.setOnItemSelectedListener(new ItemSelectedEvent());
		SpinnerBaudRateCOMD.setOnItemSelectedListener(new ItemSelectedEvent());
		DispAssistData(AssistData);
		applyPortVisibility();
	}
    //----------------------------------------------------串口号或波特率变化时，关闭打开的串口
    class ItemSelectedEvent implements Spinner.OnItemSelectedListener{
		public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
		{
			if ((arg0 == SpinnerCOMA) || (arg0 == SpinnerBaudRateCOMA))
			{
				CloseComPort(ComA);
				checkBoxAutoCOMA.setChecked(false);
				toggleButtonCOMA.setChecked(false);
			}else if ((arg0 == SpinnerCOMB) || (arg0 == SpinnerBaudRateCOMB))
			{
				CloseComPort(ComB);
				checkBoxAutoCOMA.setChecked(false);
				toggleButtonCOMB.setChecked(false);
			}else if ((arg0 == SpinnerCOMC) || (arg0 == SpinnerBaudRateCOMC))
			{
				CloseComPort(ComC);
				checkBoxAutoCOMA.setChecked(false);
				toggleButtonCOMC.setChecked(false);
			}else if ((arg0 == SpinnerCOMD) || (arg0 == SpinnerBaudRateCOMD))
			{
				CloseComPort(ComD);
				checkBoxAutoCOMA.setChecked(false);
				toggleButtonCOMD.setChecked(false);
			}
		}

		public void onNothingSelected(AdapterView<?> arg0)
		{}
    	
    }
    //----------------------------------------------------编辑框焦点转移事件
    class FocusChangeEvent implements EditText.OnFocusChangeListener{
		public void onFocusChange(View v, boolean hasFocus)
		{
			if (v==editTextCOMA)
			{
				setSendData(editTextCOMA);
			} else if (v==editTextCOMB)
			{
				setSendData(editTextCOMB);
			} else if (v==editTextCOMC)
			{
				setSendData(editTextCOMC);
			} else if (v==editTextCOMD)
			{
				setSendData(editTextCOMD);
			}else if (v==editTextTimeCOMA)
			{
				setDelayTime(editTextTimeCOMA);
			}else if (v==editTextTimeCOMB)
			{
				setDelayTime(editTextTimeCOMB);
			}else if (v==editTextTimeCOMC)
			{
				setDelayTime(editTextTimeCOMC);
			}else if (v==editTextTimeCOMD)
			{
				setDelayTime(editTextTimeCOMD);
			}
		}
    }
    //----------------------------------------------------编辑框完成事件
    class EditorActionEvent implements EditText.OnEditorActionListener{
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
		{
			if (v==editTextCOMA)
			{
				setSendData(editTextCOMA);
			} else if (v==editTextCOMB)
			{
				setSendData(editTextCOMB);
			} else if (v==editTextCOMC)
			{
				setSendData(editTextCOMC);
			} else if (v==editTextCOMD)
			{
				setSendData(editTextCOMD);
			}else if (v==editTextTimeCOMA)
			{
				setDelayTime(editTextTimeCOMA);
			}else if (v==editTextTimeCOMB)
			{
				setDelayTime(editTextTimeCOMB);
			}else if (v==editTextTimeCOMC)
			{
				setDelayTime(editTextTimeCOMC);
			}else if (v==editTextTimeCOMD)
			{
				setDelayTime(editTextTimeCOMD);
			}
			return false;
		}
    }
    //----------------------------------------------------Txt、Hex模式选择
    class radioButtonClickEvent implements RadioButton.OnClickListener{
		public void onClick(View v)
		{
			if (v==radioButtonTxt)
			{
				KeyListener TxtkeyListener = new TextKeyListener(Capitalize.NONE, false);
				editTextCOMA.setKeyListener(TxtkeyListener);
				editTextCOMB.setKeyListener(TxtkeyListener);
				editTextCOMC.setKeyListener(TxtkeyListener);
				editTextCOMD.setKeyListener(TxtkeyListener);
				AssistData.setTxtMode(true);
			}else if (v==radioButtonHex) {
				KeyListener HexkeyListener = new NumberKeyListener()
				{
					public int getInputType()
					{
						return InputType.TYPE_CLASS_TEXT;
					}
					@Override
					protected char[] getAcceptedChars()
					{
						return new char[]{'0','1','2','3','4','5','6','7','8','9',
								'a','b','c','d','e','f','A','B','C','D','E','F'};
					}
				};
				editTextCOMA.setKeyListener(HexkeyListener);
				editTextCOMB.setKeyListener(HexkeyListener);
				editTextCOMC.setKeyListener(HexkeyListener);
				editTextCOMD.setKeyListener(HexkeyListener);
				AssistData.setTxtMode(false);
			}
			editTextCOMA.setText(AssistData.getSendA());
			editTextCOMB.setText(AssistData.getSendB());
			editTextCOMC.setText(AssistData.getSendC());
			editTextCOMD.setText(AssistData.getSendD());
			setSendData(editTextCOMA);
	    	setSendData(editTextCOMB);
	    	setSendData(editTextCOMC);
	    	setSendData(editTextCOMD);
		}
    }
    //----------------------------------------------------自动发送
    class CheckBoxChangeEvent implements CheckBox.OnCheckedChangeListener{
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			if (buttonView == checkBoxAutoCOMA){
				if (!toggleButtonCOMA.isChecked() && isChecked)
				{
					buttonView.setChecked(false);
					return;
				}
				SetLoopData(ComA,editTextCOMA.getText().toString());
				SetAutoSend(ComA,isChecked);
			} else if(buttonView == checkBoxAutoCOMB){
				if (!toggleButtonCOMB.isChecked() && isChecked)
				{
					buttonView.setChecked(false);
					return;
				}
				SetLoopData(ComB,editTextCOMB.getText().toString());
				SetAutoSend(ComB,isChecked);
			} else if(buttonView == checkBoxAutoCOMC){
				if (!toggleButtonCOMC.isChecked() && isChecked)
				{
					buttonView.setChecked(false);
					return;
				}
				SetLoopData(ComC,editTextCOMC.getText().toString());
				SetAutoSend(ComC,isChecked);
			} else if(buttonView == checkBoxAutoCOMD){
				if (!toggleButtonCOMD.isChecked() && isChecked)
				{
					buttonView.setChecked(false);
					return;
				}
				SetLoopData(ComD,editTextCOMD.getText().toString());
				SetAutoSend(ComD,isChecked);
			}
		}
    }
    //----------------------------------------------------清除按钮、发送按钮
    class ButtonClickEvent implements View.OnClickListener {
		public void onClick(View v)
		{
			if (v == ButtonClear){
				editTextRecDisp.setText("");
			} else if (v== ButtonSendCOMA){
				sendPortData(ComA, editTextCOMA.getText().toString());
			}else if (v== ButtonSendCOMB){
				sendPortData(ComB, editTextCOMB.getText().toString());
			}else if (v== ButtonSendCOMC){
				sendPortData(ComC, editTextCOMC.getText().toString());
			}else if (v== ButtonSendCOMD){
				sendPortData(ComD, editTextCOMD.getText().toString());
			}		
		}
    }
    //----------------------------------------------------打开关闭串口
    class ToggleButtonCheckedChangeEvent implements ToggleButton.OnCheckedChangeListener{
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			if (buttonView == toggleButtonCOMA){
				if (isChecked){
					if (toggleButtonCOMB.isChecked() && SpinnerCOMA.getSelectedItemPosition()==SpinnerCOMB.getSelectedItemPosition())
					{
						ShowMessage("串口"+SpinnerCOMA.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMC.isChecked() && SpinnerCOMA.getSelectedItemPosition()==SpinnerCOMC.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMA.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMD.isChecked() && SpinnerCOMA.getSelectedItemPosition()==SpinnerCOMD.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMA.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else {
//						ComA=new SerialControl("/dev/s3c2410_serial0", "9600");
						ComA.setPort(SpinnerCOMA.getSelectedItem().toString());
						ComA.setBaudRate(SpinnerBaudRateCOMA.getSelectedItem().toString());
						OpenComPort(ComA);
					}
				}else {
					CloseComPort(ComA);
					checkBoxAutoCOMA.setChecked(false);
				}
			} else if (buttonView == toggleButtonCOMB){
				if (isChecked){
					if (toggleButtonCOMA.isChecked() && SpinnerCOMB.getSelectedItemPosition()==SpinnerCOMA.getSelectedItemPosition())
					{
						ShowMessage("串口"+SpinnerCOMB.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMC.isChecked() && SpinnerCOMB.getSelectedItemPosition()==SpinnerCOMC.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMB.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMD.isChecked() && SpinnerCOMB.getSelectedItemPosition()==SpinnerCOMD.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMB.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else {
//						ComB=new SerialControl("/dev/s3c2410_serial1", "9600");
						ComB.setPort(SpinnerCOMB.getSelectedItem().toString());
						ComB.setBaudRate(SpinnerBaudRateCOMB.getSelectedItem().toString());
						OpenComPort(ComB);
					}
				}else {
					CloseComPort(ComB);
					checkBoxAutoCOMB.setChecked(false);
				}
			}else if (buttonView == toggleButtonCOMC){
				if (isChecked){
					if (toggleButtonCOMA.isChecked() && SpinnerCOMC.getSelectedItemPosition()==SpinnerCOMA.getSelectedItemPosition())
					{
						ShowMessage("串口"+SpinnerCOMC.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMB.isChecked() && SpinnerCOMC.getSelectedItemPosition()==SpinnerCOMB.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMC.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMD.isChecked() && SpinnerCOMC.getSelectedItemPosition()==SpinnerCOMD.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMC.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else {
	//					ComC=new SerialControl("/dev/s3c2410_serial2", "9600");
						ComC.setPort(SpinnerCOMC.getSelectedItem().toString());
						ComC.setBaudRate(SpinnerBaudRateCOMC.getSelectedItem().toString());
						OpenComPort(ComC);
					}
				}else {
					CloseComPort(ComC);
					checkBoxAutoCOMC.setChecked(false);
				}
			}else if (buttonView == toggleButtonCOMD){
				if (isChecked){
					if (toggleButtonCOMA.isChecked() && SpinnerCOMD.getSelectedItemPosition()==SpinnerCOMA.getSelectedItemPosition())
					{
						ShowMessage("串口"+SpinnerCOMD.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMB.isChecked() && SpinnerCOMD.getSelectedItemPosition()==SpinnerCOMB.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMD.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else if (toggleButtonCOMC.isChecked() && SpinnerCOMD.getSelectedItemPosition()==SpinnerCOMC.getSelectedItemPosition()) {
						ShowMessage("串口"+SpinnerCOMD.getSelectedItem().toString()+"已打开");
						buttonView.setChecked(false);
					}else {
	//					ComD=new SerialControl("/dev/s3c2410_serial3", "9600");
						ComD.setPort(SpinnerCOMD.getSelectedItem().toString());
						ComD.setBaudRate(SpinnerBaudRateCOMD.getSelectedItem().toString());
						OpenComPort(ComD);
					}
				}else {
					CloseComPort(ComD);
					checkBoxAutoCOMD.setChecked(false);
				}
			}
		}
    }
    //----------------------------------------------------串口控制类
    private class SerialControl extends SerialHelper{

//		public SerialControl(String sPort, String sBaudRate){
//			super(sPort, sBaudRate);
//		}
		public SerialControl(){
		}

		@Override
		protected void onDataReceived(final ComBean ComRecData)
		{
			//数据接收量大或接收时弹出软键盘，界面会卡顿,可能和6410的显示性能有关
			//直接刷新显示，接收数据量大时，卡顿明显，但接收与显示同步。
			//用线程定时刷新显示可以获得较流畅的显示效果，但是接收数据速度快于显示速度时，显示会滞后。
			//最终效果差不多-_-，线程定时刷新稍好一些。
			DispQueue.AddQueue(ComRecData);//线程定时刷新显示(推荐)
			/*
			runOnUiThread(new Runnable()//直接刷新显示
			{
				public void run()
				{
					DispRecData(ComRecData);
				}
			});*/
		}
    }
    //----------------------------------------------------刷新显示线程
    private class DispQueueThread extends Thread{
		private Queue<ComBean> QueueList = new LinkedList<ComBean>(); 
		@Override
		public void run() {
			super.run();
			while(!isInterrupted()) {
				final ComBean ComData;
		        while((ComData=QueueList.poll())!=null)
		        {
		        	runOnUiThread(new Runnable()
					{
						public void run()
						{
							DispRecData(ComData);
						}
					});
		        	try
					{
		        		Thread.sleep(100);//显示性能高的话，可以把此数值调小。
					} catch (Exception e)
					{
						e.printStackTrace();
					}
		        	break;
				}
			}
		}

		public synchronized void AddQueue(ComBean ComData){
			QueueList.add(ComData);
		}
	}
    //----------------------------------------------------刷新界面数据
    private void DispAssistData(AssistBean AssistData)
	{
    	editTextCOMA.setText(AssistData.getSendA());
    	editTextCOMB.setText(AssistData.getSendB());
    	editTextCOMC.setText(AssistData.getSendC());
    	editTextCOMD.setText(AssistData.getSendD());
    	setSendData(editTextCOMA);
    	setSendData(editTextCOMB);
    	setSendData(editTextCOMC);
    	setSendData(editTextCOMD);
    	if (AssistData.isTxt())
		{
			radioButtonTxt.setChecked(true);
		} else
		{
			radioButtonHex.setChecked(true);
		}
    	editTextTimeCOMA.setText(AssistData.sTimeA);
    	editTextTimeCOMB.setText(AssistData.sTimeB);
    	editTextTimeCOMC.setText(AssistData.sTimeC);
    	editTextTimeCOMD.setText(AssistData.sTimeD);
    	setDelayTime(editTextTimeCOMA);
    	setDelayTime(editTextTimeCOMB);
    	setDelayTime(editTextTimeCOMC);
    	setDelayTime(editTextTimeCOMD);
	}
    //----------------------------------------------------保存、获取界面数据
    private void saveAssistData(AssistBean AssistData) { 
    	AssistData.sTimeA = editTextTimeCOMA.getText().toString();
    	AssistData.sTimeB = editTextTimeCOMB.getText().toString();
    	AssistData.sTimeC = editTextTimeCOMC.getText().toString();
    	AssistData.sTimeD = editTextTimeCOMD.getText().toString();
    	SharedPreferences msharedPreferences = getSharedPreferences("ComTool", Context.MODE_PRIVATE);
        try {  
            ByteArrayOutputStream baos = new ByteArrayOutputStream();  
            ObjectOutputStream oos = new ObjectOutputStream(baos);  
            oos.writeObject(AssistData); 
            String sBase64 = new String(Base64.encode(baos.toByteArray(),0)); 
            SharedPreferences.Editor editor = msharedPreferences.edit();  
            editor.putString("AssistData", sBase64);  
            editor.commit();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
    }  
    //----------------------------------------------------
    private AssistBean getAssistData() {  
    	SharedPreferences msharedPreferences = getSharedPreferences("ComTool", Context.MODE_PRIVATE);
    	AssistBean AssistData =	new AssistBean();
        try {  
            String personBase64 = msharedPreferences.getString("AssistData", "");  
            byte[] base64Bytes = Base64.decode(personBase64.getBytes(),0);  
            ByteArrayInputStream bais = new ByteArrayInputStream(base64Bytes);  
            ObjectInputStream ois = new ObjectInputStream(bais);  
            AssistData = (AssistBean) ois.readObject();
            return AssistData;
        } catch (Exception e) {  
            e.printStackTrace();  
        }
		return AssistData;  
    }  
    //----------------------------------------------------设置自动发送延时
    private void setDelayTime(TextView v){
    	if (v==editTextTimeCOMA)
		{
			AssistData.sTimeA = v.getText().toString();
			SetiDelayTime(ComA, v.getText().toString());
		}else if (v==editTextTimeCOMB)
		{
			AssistData.sTimeB = v.getText().toString();
			SetiDelayTime(ComB, v.getText().toString());
		}else if (v==editTextTimeCOMC)
		{
			AssistData.sTimeC = v.getText().toString();
			SetiDelayTime(ComC, v.getText().toString());
		}else if (v==editTextTimeCOMD)
		{
			AssistData.sTimeD = v.getText().toString();
			SetiDelayTime(ComD, v.getText().toString());
		}
    }
    //----------------------------------------------------设置自动发送数据
    private void setSendData(TextView v){
    	if (v==editTextCOMA)
		{
			AssistData.setSendA(v.getText().toString());
			SetLoopData(ComA, v.getText().toString());
		} else if (v==editTextCOMB)
		{
			AssistData.setSendB(v.getText().toString());
			SetLoopData(ComB, v.getText().toString());
		} else if (v==editTextCOMC)
		{
			AssistData.setSendC(v.getText().toString());
			SetLoopData(ComC, v.getText().toString());
		} else if (v==editTextCOMD)
		{
			AssistData.setSendD(v.getText().toString());
			SetLoopData(ComD, v.getText().toString());
		}
    }
    //----------------------------------------------------设置自动发送延时
    private void SetiDelayTime(SerialHelper ComPort,String sTime){
    	ComPort.setiDelay(Integer.parseInt(sTime));
    }
    //----------------------------------------------------设置自动发送数据
    private void SetLoopData(SerialHelper ComPort,String sLoopData){
    	if (radioButtonTxt.isChecked())
		{
			ComPort.setTxtLoopData(sLoopData);
		} else if (radioButtonHex.isChecked())
		{
			ComPort.setHexLoopData(sLoopData);
		}
    }
    //----------------------------------------------------显示接收数据
    private void DispRecData(ComBean ComRecData){
    	appendLogLine(formatFrame(ComRecData.sRecTime, ComRecData.sComPort, false, ComRecData.bRec));
    }
    private void showSentFrame(final SerialHelper port, final byte[] data){
    	if (!showTx || port == null || data == null)
		{
			return;
		}
    	final String line = formatFrame(currentTime(), port.getPort(), true, data);
    	runOnUiThread(new Runnable()
		{
			public void run()
			{
				if (showTx)
				{
					appendLogLine(line);
				}
			}
		});
    }
    private String currentTime(){
    	String pattern = showMillis ? "hh:mm:ss.SSS" : "hh:mm:ss";
    	return new SimpleDateFormat(pattern).format(new Date());
    }
    private String formatFrame(String time, String port, boolean sent, byte[] data){
    	StringBuilder sMsg=new StringBuilder();
    	sMsg.append(time);
    	sMsg.append("[");
    	sMsg.append(port);
    	sMsg.append("]");
    	if (showTx)
		{
			sMsg.append(sent ? "发送=> " : "接收=> ");
		}
    	if (radioButtonTxt != null && radioButtonTxt.isChecked())
		{
			sMsg.append("[Txt] ");
			sMsg.append(new String(data));
		}else {
			sMsg.append("[Hex] ");
			sMsg.append(MyFunc.ByteArrToHex(data));
		}
    	sMsg.append("\r\n");
    	return sMsg.toString();
    }
    private void appendLogLine(String line){
    	editTextRecDisp.append(line);
    	iRecLines++;
    	if ((iRecLines > 500) && (checkBoxAutoClear.isChecked()))
		{
    		editTextRecDisp.setText("");
    		iRecLines=0;
		}
    	if (showSaveLog)
		{
			appendLogFile(line);
		}
    }
    //----------------------------------------------------设置自动发送模式开关
    private void SetAutoSend(SerialHelper ComPort,boolean isAutoSend){
    	if (isAutoSend)
		{
    		ComPort.startSend();
		} else
		{
			ComPort.stopSend();
		}
    }
    //----------------------------------------------------串口发送
    private void sendPortData(SerialHelper ComPort,String sOut){
    	if (ComPort!=null && ComPort.isOpen())
		{
    		if (radioButtonTxt.isChecked())
			{
				ComPort.sendTxt(sOut);
			}else if (radioButtonHex.isChecked()) {
				ComPort.sendHex(sOut);
			}
		}
    }
    //----------------------------------------------------关闭串口
    private void CloseComPort(SerialHelper ComPort){
    	if (ComPort!=null){
    		ComPort.stopSend();
    		ComPort.close();
		}
    }
    //----------------------------------------------------打开串口
    private void OpenComPort(SerialHelper ComPort){
    	try
		{
			ComPort.open();
		} catch (SecurityException e) {
			ShowMessage("打开串口失败:没有串口读/写权限!");
		} catch (IOException e) {
			ShowMessage("打开串口失败:未知错误!");
		} catch (InvalidParameterException e) {
			ShowMessage("打开串口失败:参数错误!");
		}
    }
    //------------------------------------------显示消息
  	private void ShowMessage(String sMsg)
  	{
  		Toast.makeText(this, sMsg, Toast.LENGTH_SHORT).show();
  	}

	private SharedPreferences getSettings(){
		return getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
	}
	private void loadSettings(){
		SharedPreferences pref = getSettings();
		showMillis = pref.getBoolean(KEY_MILLIS, false);
		showTx = pref.getBoolean(KEY_TX, false);
		showMulti = pref.getBoolean(KEY_MULTI, false);
		showSaveLog = pref.getBoolean(KEY_SAVE, false) && hasStoragePermission();
		ComBean.showMillis = showMillis;
	}
	private void saveSetting(String key, boolean value){
		getSettings().edit().putBoolean(key, value).apply();
	}
	private void bindSendLogger(){
		ComA.setOnDataSentListener(new SerialHelper.OnDataSentListener() {
			public void onDataSent(byte[] data) { showSentFrame(ComA, data); }
		});
		ComB.setOnDataSentListener(new SerialHelper.OnDataSentListener() {
			public void onDataSent(byte[] data) { showSentFrame(ComB, data); }
		});
		ComC.setOnDataSentListener(new SerialHelper.OnDataSentListener() {
			public void onDataSent(byte[] data) { showSentFrame(ComC, data); }
		});
		ComD.setOnDataSentListener(new SerialHelper.OnDataSentListener() {
			public void onDataSent(byte[] data) { showSentFrame(ComD, data); }
		});
	}
	private void applyPortVisibility(){
		int extra = showMulti ? View.VISIBLE : View.GONE;
		setViewVisibility(R.id.LinearLayoutCOMB, extra);
		setViewVisibility(R.id.LinearLayoutCOMC, extra);
		setViewVisibility(R.id.LinearLayoutCOMD, extra);
		setViewVisibility(R.id.LinearLayoutCOMCD, extra);
		if (!showMulti)
		{
			if (toggleButtonCOMB != null && toggleButtonCOMB.isChecked()) toggleButtonCOMB.setChecked(false);
			if (toggleButtonCOMC != null && toggleButtonCOMC.isChecked()) toggleButtonCOMC.setChecked(false);
			if (toggleButtonCOMD != null && toggleButtonCOMD.isChecked()) toggleButtonCOMD.setChecked(false);
			CloseComPort(ComB);
			CloseComPort(ComC);
			CloseComPort(ComD);
		}
	}
	private void setViewVisibility(int id, int visibility){
		View view = findViewById(id);
		if (view != null)
		{
			view.setVisibility(visibility);
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if (item.getItemId() == R.id.action_settings)
		{
			showSettingsDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	private void showSettingsDialog(){
		View content = getLayoutInflater().inflate(R.layout.dialog_settings, null);
		final CheckBox checkMillis = (CheckBox)content.findViewById(R.id.checkShowMillis);
		final CheckBox checkTx = (CheckBox)content.findViewById(R.id.checkShowTx);
		final CheckBox checkMulti = (CheckBox)content.findViewById(R.id.checkShowMulti);
		final CheckBox checkSave = (CheckBox)content.findViewById(R.id.checkSaveLog);
		Button clearLog = (Button)content.findViewById(R.id.buttonClearLog);
		checkMillis.setChecked(showMillis);
		checkTx.setChecked(showTx);
		checkMulti.setChecked(showMulti);
		checkSave.setChecked(getSettings().getBoolean(KEY_SAVE, false));
		dialogSaveCheck = checkSave;
		checkMillis.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				showMillis = isChecked;
				ComBean.showMillis = isChecked;
				saveSetting(KEY_MILLIS, isChecked);
			}
		});
		checkTx.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				showTx = isChecked;
				saveSetting(KEY_TX, isChecked);
			}
		});
		checkMulti.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				showMulti = isChecked;
				saveSetting(KEY_MULTI, isChecked);
				applyPortVisibility();
			}
		});
		checkSave.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked && !hasStoragePermission())
				{
					saveSetting(KEY_SAVE, true);
					showSaveLog = false;
					requestStoragePermission();
					ShowMessage("请授予存储权限后再保存日志");
					return;
				}
				showSaveLog = isChecked;
				saveSetting(KEY_SAVE, isChecked);
			}
		});
		clearLog.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				clearLogFile();
			}
		});
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("设置")
				.setView(content)
				.create();
		dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
			public void onDismiss(android.content.DialogInterface dialogInterface) {
				dialogSaveCheck = null;
			}
		});
		dialog.show();
	}
	private boolean hasStoragePermission(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			return Environment.isExternalStorageManager();
		}
		return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}
	private void requestStoragePermission(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			try
			{
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				intent.setData(Uri.parse("package:" + getPackageName()));
				startActivity(intent);
			}
			catch (Exception e)
			{
				startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
			}
		}
		else
		{
			requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
		}
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQ_STORAGE)
		{
			showSaveLog = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
					&& getSettings().getBoolean(KEY_SAVE, false);
			if (dialogSaveCheck != null)
			{
				dialogSaveCheck.setOnCheckedChangeListener(null);
				dialogSaveCheck.setChecked(showSaveLog);
				dialogSaveCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (isChecked && !hasStoragePermission())
						{
							saveSetting(KEY_SAVE, true);
							showSaveLog = false;
							requestStoragePermission();
							ShowMessage("请授予存储权限后再保存日志");
							return;
						}
						showSaveLog = isChecked;
						saveSetting(KEY_SAVE, isChecked);
					}
				});
			}
			if (!showSaveLog && getSettings().getBoolean(KEY_SAVE, false) && grantResults.length > 0
					&& grantResults[0] != PackageManager.PERMISSION_GRANTED)
			{
				saveSetting(KEY_SAVE, false);
				ShowMessage("未授予存储权限，已关闭保存日志");
			}
		}
	}
	private File logFile(){
		return new File(Environment.getExternalStorageDirectory(), "com_log.txt");
	}
	private void appendLogFile(final String line){
		if (logWriter == null)
		{
			return;
		}
		logWriter.execute(new Runnable() {
			public void run() {
				FileOutputStream out = null;
				try
				{
					out = new FileOutputStream(logFile(), true);
					out.write(line.getBytes("UTF-8"));
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				finally
				{
					if (out != null)
					{
						try { out.close(); } catch (IOException ignored) {}
					}
				}
			}
		});
	}
	private void clearLogFile(){
		if (!hasStoragePermission())
		{
			requestStoragePermission();
			ShowMessage("请授予存储权限后再清除日志");
			return;
		}
		if (logWriter == null)
		{
			return;
		}
		logWriter.execute(new Runnable() {
			public void run() {
				File file = logFile();
				final boolean missing = !file.exists();
				final boolean deleted = !missing && file.delete();
				runOnUiThread(new Runnable() {
					public void run() {
						if (missing) ShowMessage("日志文件不存在");
						else if (deleted) ShowMessage("已清除 /sdcard/com_log.txt");
						else ShowMessage("清除日志失败");
					}
				});
			}
		});
	}
}