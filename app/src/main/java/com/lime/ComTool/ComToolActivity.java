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
import java.util.concurrent.Future;
import com.lime.bean.AssistBean;
import com.lime.bean.ComBean;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.KeyListener;
import android.text.method.NumberKeyListener;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.style.ForegroundColorSpan;
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
	final ArrayList<Long> logTimes = new ArrayList<Long>();
	boolean showMillis=false;
	boolean showCount=false;
	boolean showTx=false;
	boolean showMulti=false;
	boolean showSaveLog=false;
	int sendCount=0;
	TextView textSendCount;
	boolean persistSpinners=false;
	CheckBox dialogSaveCheck;
	ExecutorService logWriter;
	ExecutorService commandExecutor;
	Future<?> sendFutureA,sendFutureB,sendFutureC,sendFutureD;
	static final int REPLY_TIMEOUT_MS=10;
	static final String PREF_SETTINGS="ComToolSettings";
	static final String KEY_MILLIS="show_millis";
	static final String KEY_COUNT="show_count";
	static final String KEY_TX="show_tx";
	static final String KEY_MULTI="show_multi";
	static final String KEY_SAVE="save_log";
	static final String KEY_TXT="txt_mode";
	static final String KEY_AUTO_CLEAR="auto_clear";
	static final String KEY_PORT_A="port_a";
	static final String KEY_PORT_B="port_b";
	static final String KEY_PORT_C="port_c";
	static final String KEY_PORT_D="port_d";
	static final String KEY_BAUD_A="baud_a";
	static final String KEY_BAUD_B="baud_b";
	static final String KEY_BAUD_C="baud_c";
	static final String KEY_BAUD_D="baud_d";
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
        commandExecutor = Executors.newCachedThreadPool();
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
    	cancelCommand(ComA);
    	cancelCommand(ComB);
    	cancelCommand(ComC);
    	cancelCommand(ComD);
    	if (logWriter != null)
		{
			logWriter.shutdown();
		}
    	if (commandExecutor != null)
		{
			commandExecutor.shutdownNow();
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
    	logTimes.clear();
    	iRecLines = 0;
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
    	textSendCount=(TextView)findViewById(R.id.textSendCount);
    	updateSendCount();
    	
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
    	findViewById(R.id.ButtonCopy).setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMA.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMB.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMC.setOnClickListener(new ButtonClickEvent());
    	ButtonSendCOMD.setOnClickListener(new ButtonClickEvent());
    	toggleButtonCOMA.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMB.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMC.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	toggleButtonCOMD.setOnCheckedChangeListener(new ToggleButtonCheckedChangeEvent());
    	checkBoxAutoClear.setChecked(getSettings().getBoolean(KEY_AUTO_CLEAR, true));
    	checkBoxAutoClear.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				saveSetting(KEY_AUTO_CLEAR, isChecked);
			}
		});
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
    	selectSavedItem(SpinnerBaudRateCOMA, KEY_BAUD_A, 12);
    	selectSavedItem(SpinnerBaudRateCOMB, KEY_BAUD_B, 12);
    	selectSavedItem(SpinnerBaudRateCOMC, KEY_BAUD_C, 12);
    	selectSavedItem(SpinnerBaudRateCOMD, KEY_BAUD_D, 12);
    	
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
		selectSavedItem(SpinnerCOMA, KEY_PORT_A, 0);
		selectSavedItem(SpinnerCOMB, KEY_PORT_B, 1);
		selectSavedItem(SpinnerCOMC, KEY_PORT_C, 2);
		selectSavedItem(SpinnerCOMD, KEY_PORT_D, 3);
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
		persistSpinners = false;
		editTextRecDisp.post(new Runnable() {
			public void run() {
				persistSpinners = true;
			}
		});
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
			if (persistSpinners)
			{
				savePortAndBaud();
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
				applyInputMode(true);
				saveSetting(KEY_TXT, true);
			}else if (v==radioButtonHex) {
				applyInputMode(false);
				saveSetting(KEY_TXT, false);
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
				clearLogDisplay();
			} else if (v.getId() == R.id.ButtonCopy){
				copyLog();
			} else if (v== ButtonSendCOMA){
				countSendClick();
				sendPortData(ComA, editTextCOMA.getText().toString());
			}else if (v== ButtonSendCOMB){
				countSendClick();
				sendPortData(ComB, editTextCOMB.getText().toString());
			}else if (v== ButtonSendCOMC){
				countSendClick();
				sendPortData(ComC, editTextCOMC.getText().toString());
			}else if (v== ButtonSendCOMD){
				countSendClick();
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

		private final Object replyLock = new Object();
		private long replyAt;

		void armReply(){
			synchronized (replyLock)
			{
				replyAt = 0L;
			}
		}
		@Override
		protected void onBeforeDataReceived(){
			synchronized (replyLock)
			{
				replyAt = SystemClock.elapsedRealtime();
				replyLock.notifyAll();
			}
		}
		boolean waitForReply(int timeoutMs) throws InterruptedException{
			long start = SystemClock.elapsedRealtime();
			synchronized (replyLock)
			{
				while (true)
				{
					if (Thread.currentThread().isInterrupted())
					{
						throw new InterruptedException();
					}
					long now = SystemClock.elapsedRealtime();
					long rx = replyAt;
					if (rx > 0)
					{
						long silentFor = now - rx;
						if (silentFor >= timeoutMs)
						{
							return true;
						}
						replyLock.wait(Math.max(1, timeoutMs - silentFor));
					} else
					{
						long remain = timeoutMs - (now - start);
						if (remain <= 0)
						{
							if (replyAt > 0)
							{
								continue;
							}
							return false;
						}
						replyLock.wait(remain);
					}
				}
			}
		}

		@Override
		protected void onDataReceived(final ComBean ComRecData)
		{
			//数据接收量大或接收时弹出软键盘，界面会卡顿,可能和6410的显示性能有关
			//直接刷新显示，接收数据量大时，卡顿明显，但接收与显示同步。
			//用线程定时刷新显示可以获得较流畅的显示效果，但是接收数据速度快于显示速度时，显示会滞后。
			//最终效果差不多-_-，线程定时刷新稍好一些。
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					DispRecData(ComRecData);
				}
			});
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
    	boolean txtMode = getSettings().contains(KEY_TXT)
    			? getSettings().getBoolean(KEY_TXT, true)
    			: AssistData.isTxt();
    	applyInputMode(txtMode);
    	saveSetting(KEY_TXT, txtMode);
    	editTextCOMA.setText(AssistData.getSendA());
    	editTextCOMB.setText(AssistData.getSendB());
    	editTextCOMC.setText(AssistData.getSendC());
    	editTextCOMD.setText(AssistData.getSendD());
    	setSendData(editTextCOMA);
    	setSendData(editTextCOMB);
    	setSendData(editTextCOMC);
    	setSendData(editTextCOMD);
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
    	appendLogLine(ComRecData.lRecTime, formatFrame(ComRecData.sRecTime, ComRecData.sComPort, false, ComRecData.bRec));
    }
    private void showSentFrame(final SerialHelper port, final byte[] data){
    	if (!showTx || port == null || data == null)
		{
			return;
		}
    	final long timeMillis = System.currentTimeMillis();
    	final String line = formatFrame(formatTime(timeMillis), port.getPort(), true, data);
    	runOnUiThread(new Runnable()
		{
			public void run()
			{
				if (showTx)
				{
					appendLogLine(timeMillis, line);
				}
			}
		});
    }
    private String currentTime(){
    	return formatTime(System.currentTimeMillis());
    }
    private String formatTime(long timeMillis){
    	String pattern = showMillis ? "hh:mm:ss.SSS" : "hh:mm:ss";
    	return new SimpleDateFormat(pattern).format(new Date(timeMillis));
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
    private void appendTimeoutLine(SerialHelper port, String command){
    	long timeMillis = System.currentTimeMillis();
    	String line = formatTime(timeMillis) + "[" + port.getPort() + "]接收=> 超时(" + command + ")\r\n";
    	SpannableString span = new SpannableString(line);
    	span.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.timeout_text, getTheme())),
    			0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    	appendLogLine(timeMillis, span);
    }
    private void appendLogLine(long timeMillis, CharSequence line){
    	int index = logTimes.size();
    	while (index > 0 && logTimes.get(index - 1) > timeMillis)
		{
			index--;
		}
    	editTextRecDisp.getText().insert(offsetOfLogLine(index), line);
    	logTimes.add(index, timeMillis);
    	iRecLines++;
    	if ((iRecLines > 500) && (checkBoxAutoClear.isChecked()))
		{
    		clearLogDisplay();
		}
    	if (showSaveLog)
		{
			appendLogFile(line.toString());
		}
    }
    private int offsetOfLogLine(int lineIndex){
    	if (lineIndex <= 0)
		{
			return 0;
		}
    	CharSequence text = editTextRecDisp.getText();
    	if (lineIndex >= logTimes.size())
		{
			return text.length();
		}
    	int count = 0;
    	for (int i = 0; i < text.length(); i++)
		{
			if (text.charAt(i) == '\n')
			{
				count++;
				if (count == lineIndex)
				{
					return i + 1;
				}
			}
		}
    	return text.length();
    }
    private void clearLogDisplay(){
    	editTextRecDisp.setText("");
    	logTimes.clear();
    	iRecLines = 0;
    }
    private void countSendClick(){
    	sendCount++;
    	updateSendCount();
    }
    private void updateSendCount(){
    	if (textSendCount == null)
		{
			return;
		}
    	textSendCount.setText("计数：" + sendCount);
    	textSendCount.setVisibility(showCount ? View.VISIBLE : View.GONE);
    }
    private void copyLog(){
    	CharSequence text = editTextRecDisp.getText();
    	if (text == null || text.length() == 0)
		{
			ShowMessage("日志为空");
			return;
		}
    	ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    	if (clipboard == null)
		{
			ShowMessage("复制失败");
			return;
		}
    	clipboard.setPrimaryClip(ClipData.newPlainText("com_log", text.toString()));
    	ShowMessage("已复制");
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
    private void sendPortData(final SerialControl ComPort,String sOut){
    	if (ComPort==null || !ComPort.isOpen() || commandExecutor==null)
		{
			return;
		}
    	final List<String> lines = splitCommands(sOut);
    	if (lines.isEmpty())
		{
			return;
		}
    	final boolean hex = radioButtonHex.isChecked();
    	Future<?> previous = swapSendFuture(ComPort, null);
    	if (previous != null)
		{
			previous.cancel(true);
		}
    	Future<?> task = commandExecutor.submit(new Runnable() {
			public void run() {
				for (int i = 0; i < lines.size(); i++)
				{
					if (Thread.currentThread().isInterrupted() || !ComPort.isOpen())
					{
						return;
					}
					String line = lines.get(i);
					byte[] data;
					try
					{
						data = hex ? MyFunc.HexToByteArr(line) : line.getBytes();
					} catch (Exception e)
					{
						final String bad = line;
						runOnUiThread(new Runnable() {
							public void run() {
								ShowMessage("命令格式错误: " + bad);
							}
						});
						continue;
					}
					if (data == null || data.length == 0)
					{
						continue;
					}
					ComPort.armReply();
					ComPort.send(data);
					boolean replied;
					try
					{
						replied = ComPort.waitForReply(REPLY_TIMEOUT_MS);
					} catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return;
					}
					if (!replied)
					{
						final String command = line;
						runOnUiThread(new Runnable() {
							public void run() {
								appendTimeoutLine(ComPort, command);
							}
						});
					}
				}
			}
		});
    	swapSendFuture(ComPort, task);
    }
    private List<String> splitCommands(String text){
    	List<String> lines = new ArrayList<String>();
    	if (text == null)
		{
			return lines;
		}
    	String[] parts = text.split("\\r?\\n");
    	for (int i = 0; i < parts.length; i++)
		{
			String line = parts[i].trim();
			if (line.length() > 0)
			{
				lines.add(line);
			}
		}
    	return lines;
    }
    private Future<?> swapSendFuture(SerialHelper port, Future<?> next){
    	Future<?> old;
    	if (port == ComA)
		{
			old = sendFutureA;
			sendFutureA = next;
		} else if (port == ComB)
		{
			old = sendFutureB;
			sendFutureB = next;
		} else if (port == ComC)
		{
			old = sendFutureC;
			sendFutureC = next;
		} else
		{
			old = sendFutureD;
			sendFutureD = next;
		}
    	return old;
    }
    private void cancelCommand(SerialHelper port){
    	Future<?> future = swapSendFuture(port, null);
    	if (future != null)
		{
			future.cancel(true);
		}
    }
    //----------------------------------------------------关闭串口
    private void CloseComPort(SerialHelper ComPort){
    	if (ComPort!=null){
    		cancelCommand(ComPort);
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
		showCount = pref.getBoolean(KEY_COUNT, false);
		showTx = pref.getBoolean(KEY_TX, false);
		showMulti = pref.getBoolean(KEY_MULTI, false);
		showSaveLog = pref.getBoolean(KEY_SAVE, false) && hasStoragePermission();
		ComBean.showMillis = showMillis;
	}
	private void saveSetting(String key, boolean value){
		getSettings().edit().putBoolean(key, value).apply();
	}
	private void selectSavedItem(Spinner spinner, String key, int fallback){
		if (spinner.getCount() <= 0)
		{
			return;
		}
		String saved = getSettings().getString(key, "");
		int index = fallback;
		if (saved != null && saved.length() > 0)
		{
			for (int i = 0; i < spinner.getCount(); i++)
			{
				Object item = spinner.getItemAtPosition(i);
				if (item != null && saved.equals(item.toString()))
				{
					index = i;
					break;
				}
			}
		}
		if (index >= spinner.getCount())
		{
			index = spinner.getCount() - 1;
		}
		if (index < 0)
		{
			index = 0;
		}
		spinner.setSelection(index);
	}
	private void savePortAndBaud(){
		SharedPreferences.Editor editor = getSettings().edit();
		putSpinnerValue(editor, KEY_PORT_A, SpinnerCOMA);
		putSpinnerValue(editor, KEY_PORT_B, SpinnerCOMB);
		putSpinnerValue(editor, KEY_PORT_C, SpinnerCOMC);
		putSpinnerValue(editor, KEY_PORT_D, SpinnerCOMD);
		putSpinnerValue(editor, KEY_BAUD_A, SpinnerBaudRateCOMA);
		putSpinnerValue(editor, KEY_BAUD_B, SpinnerBaudRateCOMB);
		putSpinnerValue(editor, KEY_BAUD_C, SpinnerBaudRateCOMC);
		putSpinnerValue(editor, KEY_BAUD_D, SpinnerBaudRateCOMD);
		editor.apply();
	}
	private void putSpinnerValue(SharedPreferences.Editor editor, String key, Spinner spinner){
		Object item = spinner.getSelectedItem();
		if (item != null)
		{
			editor.putString(key, item.toString());
		}
	}
	private void applyInputMode(boolean txt){
		AssistData.setTxtMode(txt);
		if (txt)
		{
			KeyListener txtKeyListener = new TextKeyListener(Capitalize.NONE, false);
			editTextCOMA.setKeyListener(txtKeyListener);
			editTextCOMB.setKeyListener(txtKeyListener);
			editTextCOMC.setKeyListener(txtKeyListener);
			editTextCOMD.setKeyListener(txtKeyListener);
			radioButtonTxt.setChecked(true);
		} else
		{
			KeyListener hexKeyListener = new NumberKeyListener()
			{
				public int getInputType()
				{
					return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
				}
				@Override
				protected char[] getAcceptedChars()
				{
					return new char[]{'0','1','2','3','4','5','6','7','8','9',
							'a','b','c','d','e','f','A','B','C','D','E','F',
							' ','\n','\r'};
				}
			};
			editTextCOMA.setKeyListener(hexKeyListener);
			editTextCOMB.setKeyListener(hexKeyListener);
			editTextCOMC.setKeyListener(hexKeyListener);
			editTextCOMD.setKeyListener(hexKeyListener);
			radioButtonHex.setChecked(true);
		}
		allowMultiLine(editTextCOMA);
		allowMultiLine(editTextCOMB);
		allowMultiLine(editTextCOMC);
		allowMultiLine(editTextCOMD);
	}
	private void allowMultiLine(EditText edit){
		edit.setSingleLine(false);
		edit.setHorizontallyScrolling(false);
		edit.setMaxLines(Integer.MAX_VALUE);
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
		final CheckBox checkCount = (CheckBox)content.findViewById(R.id.checkShowCount);
		final CheckBox checkTx = (CheckBox)content.findViewById(R.id.checkShowTx);
		final CheckBox checkMulti = (CheckBox)content.findViewById(R.id.checkShowMulti);
		final CheckBox checkSave = (CheckBox)content.findViewById(R.id.checkSaveLog);
		Button clearLog = (Button)content.findViewById(R.id.buttonClearLog);
		checkMillis.setChecked(showMillis);
		checkCount.setChecked(showCount);
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
		checkCount.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				showCount = isChecked;
				saveSetting(KEY_COUNT, isChecked);
				updateSendCount();
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
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(content)
				.create();
		content.findViewById(R.id.buttonCloseSettings).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
			public void onDismiss(android.content.DialogInterface dialogInterface) {
				dialogSaveCheck = null;
			}
		});
		dialog.show();
		View dialogParent = (View) content.getParent();
		if (dialogParent != null)
		{
			dialogParent.setPadding(0, 0, 0, 0);
		}
		if (dialog.getWindow() != null)
		{
			dialog.getWindow().setBackgroundDrawableResource(R.color.card);
		}
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