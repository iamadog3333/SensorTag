/**************************************************************************************************
  Filename:       DeviceActivity.java
  Revised:        $Date: 2013-09-05 07:58:48 +0200 (to, 05 sep 2013) $
  Revision:       $Revision: 27616 $

  Copyright 2013 Texas Instruments Incorporated. All rights reserved.
 
  IMPORTANT: Your use of this Software is limited to those specific rights
  granted under the terms of a software license agreement between the user
  who downloaded the software, his/her employer (which must be your employer)
  and Texas Instruments Incorporated (the "License").  You may not use this
  Software unless you agree to abide by the terms of the License. 
  The License limits your use, and you acknowledge, that the Software may not be 
  modified, copied or distributed unless used solely and exclusively in conjunction 
  with a Texas Instruments Bluetooth device. Other than for the foregoing purpose, 
  you may not use, reproduce, copy, prepare derivative works of, modify, distribute, 
  perform, display or sell this Software and/or its documentation for any purpose.
 
  YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
  PROVIDED 揂S IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
  INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
  NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
  TEXAS INSTRUMENTS OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT,
  NEGLIGENCE, STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER
  LEGAL EQUITABLE THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES
  INCLUDING BUT NOT LIMITED TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE
  OR CONSEQUENTIAL DAMAGES, LOST PROFITS OR LOST DATA, COST OF PROCUREMENT
  OF SUBSTITUTE GOODS, TECHNOLOGY, SERVICES, OR ANY CLAIMS BY THIRD PARTIES
  (INCLUDING BUT NOT LIMITED TO ANY DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 
  Should you have any questions regarding your right to use this Software,
  contact Texas Instruments Incorporated at www.TI.com

 **************************************************************************************************/
package ti.android.ble.sensortag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ti.android.ble.common.BluetoothLeService;
import ti.android.ble.common.GattInfo;
import ti.android.ble.common.HelpView;
import ti.android.util.Point3D;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import java.util.UUID; //张强 20140208

public class DeviceActivity extends ViewPagerActivity {
  // Log
  private static String TAG = "DeviceActivity";

	// Activity
	public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;

	private DeviceView mDeviceView = null;

  // BLE
  private BluetoothLeService mBtLeService = null;
  private BluetoothDevice mBluetoothDevice = null;
  private BluetoothGatt mBtGatt = null;
  private List<BluetoothGattService> mServiceList = null;
  private List<BluetoothGattCharacteristic> mCharacterList = null; //张强 20140208
  private BluetoothGattCharacteristic mChar = null;  //张强 20140208
  private static final int GATT_TIMEOUT = 100; // milliseconds
  private boolean mServicesRdy = false;
	private boolean mIsReceiving = false;

  // SensorTag
  private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
  private BluetoothGattService mOadService = null;
  private BluetoothGattService mConnControlService = null;
  private BluetoothGattService mFFE0Service = null; //张强 20140208
  private boolean mMagCalibrateRequest = true;
  private boolean mHeightCalibrateRequest = true;
  //张强 20140208
  public static UUID uuid_test_show = null;
  private boolean Write_Char_Success = false;
  
  public DeviceActivity() {
    mResourceFragmentPager = R.layout.fragment_pager;
    mResourceIdPager = R.id.pager;
  }

  public static DeviceActivity getInstance() {
	  return (DeviceActivity) mThis;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();

    // GUI
    mDeviceView = new DeviceView();
    mSectionsPagerAdapter.addSection(mDeviceView, "Services");
    mSectionsPagerAdapter.addSection(new HelpView("help_device.html", R.layout.fragment_help, R.id.webpage), "Help");
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

    // BLE
    mBtLeService = BluetoothLeService.getInstance();
    mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
    mServiceList = new ArrayList<BluetoothGattService>();

    // GATT database
    Resources res = getResources();
    XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
    new GattInfo(xpp);
    
    // Initialize sensor list
    updateSensorList();
  }

	@Override
  public void onDestroy() {
    super.onDestroy();
    finishActivity(PREF_ACT_REQ);
    finishActivity(FWUPDATE_ACT_REQ);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu items for use in the action bar
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.device_activity_actions, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    switch (item.getItemId()) {
    case R.id.opt_prefs:
    	startPrefrenceActivity();
      break;
    case R.id.opt_fwupdate:
      startOadActivity();
      break;
    case R.id.opt_about:
      openAboutDialog();
      break;
    default:
      return super.onOptionsItemSelected(item);
    }
    return true;
  }

  @Override 
  protected void onResume()
  {
  	Log.d(TAG,"onResume");
    super.onResume();
    if (!mIsReceiving) {
    	registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    	mIsReceiving = true;
    }
  }

  @Override
  protected void onPause() {
  	Log.d(TAG,"onPause");
  	super.onPause();
  	if (mIsReceiving) {
  		unregisterReceiver(mGattUpdateReceiver);
  		mIsReceiving = false;
  	}
  }

  private static IntentFilter makeGattUpdateIntentFilter() {
  	final IntentFilter fi = new IntentFilter();
  	fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
  	fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
  	fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
  	fi.addAction(BluetoothLeService.ACTION_DATA_READ);
  	return fi;
  }

  void onViewInflated(View view) {
    Log.d(TAG, "Gatt view ready");

    // Set title bar to device name
    setTitle(mBluetoothDevice.getName());

    // Create GATT object
    mBtGatt = BluetoothLeService.getBtGatt();

    // Start service discovery
    if (!mServicesRdy && mBtGatt != null) {
      if (mBtLeService.getNumServices() == 0)
        discoverServices();
      else
        displayServices();
    }
  }

  //
  // Application implementation
  //
  private void updateSensorList() {
  	mEnabledSensors.clear();

  	for (int i=0; i<Sensor.SENSOR_LIST.length; i++) {
  		Sensor sensor = Sensor.SENSOR_LIST[i];
  		if (isEnabledByPrefs(sensor)) {
  			mEnabledSensors.add(sensor);
  		}
  	}
  }


  boolean isEnabledByPrefs(final Sensor sensor) {
    String preferenceKeyString = "pref_" + sensor.name().toLowerCase(Locale.ENGLISH) + "_on";

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    Boolean defaultValue = true;
    return prefs.getBoolean(preferenceKeyString, defaultValue);
  }
  

  BluetoothGattService getOadService() {
    return mOadService;
  }

  BluetoothGattService getConnControlService() {
    return mConnControlService;
  }

  private void startOadActivity() {
    if (mOadService != null && mConnControlService != null) {
    	// Launch OAD
      enableSensors(false);
      enableNotifications(false);
      Toast.makeText(this, "OAD service available", Toast.LENGTH_LONG).show();
      final Intent i = new Intent(this, FwUpdateActivity.class);
      startActivityForResult(i, FWUPDATE_ACT_REQ);
    } else {
      Toast.makeText(this, "OAD service not available on this device", Toast.LENGTH_LONG).show();
    }
  }

  private void startPrefrenceActivity() {
  	// Disable sensors and notifications when settings dialog is open
    enableSensors(false);
    enableNotifications(false);

    final Intent i = new Intent(this, PreferencesActivity.class);
    i.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, PreferencesFragment.class.getName());
    i.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
    i.putExtra(EXTRA_DEVICE, mBluetoothDevice);
    startActivityForResult(i,PREF_ACT_REQ);		
	}


  private void checkOad() {
    // Check if OAD is supported (needs OAD and Connection Control service)
    mOadService = null;
    mConnControlService = null;

    for (int i = 0; i < mServiceList.size() && (mOadService == null || mConnControlService == null); i++) {
      BluetoothGattService srv = mServiceList.get(i);
      if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
        mOadService = srv;
      }
      if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
        mConnControlService = srv;
      }
    }
  }


  private void discoverServices() {
    if (mBtGatt.discoverServices()) {
      Log.i(TAG, "START SERVICE DISCOVERY");
      mServiceList.clear();
      setStatus("Service discovery started");
    } else {
      setError("Service discovery start failed");
    }
  }

  private void displayServices() {
    mServicesRdy = true;

    try {
      mServiceList = mBtLeService.getSupportedGattServices();
    } catch (Exception e) {
      e.printStackTrace();
      mServicesRdy = false;
    }

    // Characteristics descriptor readout done
    if (mServicesRdy) {
      setStatus("Service discovery complete");
    //张强 20140208
//      enableSensors(true);
//      enableNotifications(true);
    } else {
      setError("Failed to read services");
    }
    //张强 20140208 把设备的所有服务的UUid都显示出来
    for (int i = 0; i < mServiceList.size(); i++) 
        {BluetoothGattService srv = mServiceList.get(i);
        System.out.println("service_uuid"+i+":"+srv.getUuid()); 
     /*   if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
          mOadService = srv;
        }
        if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
          mConnControlService = srv;
        }*/
      }
    pair_senroro_beacon();
  }
  
  private void pair_senroro_beacon()
  {
	  mFFE0Service = null;
	  int FFE0ServiceFound=0;
	  int CharacterFound=1;
	  for (int i = 0; i < mServiceList.size(); i++) 
      {  //找出beacon中的FFE0服务
		  BluetoothGattService srv = mServiceList.get(i);
          if (srv.getUuid().equals(GattInfo.SERVICE_UUID_FFE0)) 
              {mFFE0Service = srv;
               FFE0ServiceFound=1;} 
      }
	  if(FFE0ServiceFound==1)
	  {  //读出FFE0服务中的所有character
		  try {
			  mCharacterList=mFFE0Service.getCharacteristics ();
		    } catch (Exception e) {
		      e.printStackTrace();
		      CharacterFound= 0;
		    }
		  if(CharacterFound==1)
		  {   //找出Characteristic 6
			  for (int i = 0; i < mCharacterList.size(); i++) 
		        {BluetoothGattCharacteristic character = mCharacterList.get(i);
		        //显示所有的Character
		        //System.out.println("Char_uuid"+i+":"+character.getUuid()); 
		        if(character.getUuid().equals(GattInfo.Char6_UUID))
		        	mChar = character;
		        }
			  //往Characteristic 6写入数据
			  byte[] Buff={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
			  boolean ok = false;
			  ok = mChar.setValue(Buff);
			  /*if(ok)
			     {System.out.println("Write to char6 Success!");}
			  else
			     {System.out.println("Write to char6 Failed!");} 
			  */   
			  //其实以上代码并没有写入数据，还需要
			  mBtGatt.writeCharacteristic(mChar);
			  System.out.println("往Characteristic 6写入数据.");
			
		   }
	  }
	  
  }

  private void setError(String txt) {
  	if (mDeviceView != null)
  		mDeviceView.setError(txt);
  }

  private void setStatus(String txt) {
  	if (mDeviceView != null)
  		mDeviceView.setStatus(txt);
  }

  private void enableSensors(boolean enable) {
  	for (Sensor sensor : mEnabledSensors) {
  		UUID servUuid = sensor.getService();
  		UUID confUuid = sensor.getConfig();
  		
  		// Skip keys 
  		if (confUuid == null)
  			break;

  		// Barometer calibration
			if (confUuid.equals(SensorTag.UUID_BAR_CONF) && enable) {
				calibrateBarometer();
			}
			
  		BluetoothGattService serv = mBtGatt.getService(servUuid);
  		BluetoothGattCharacteristic charac = serv.getCharacteristic(confUuid);
  		byte value =  enable ? sensor.getEnableSensorCode() : Sensor.DISABLE_SENSOR_CODE;
  		mBtLeService.writeCharacteristic(charac, value);
			mBtLeService.waitIdle(GATT_TIMEOUT);
  	}
  	
  }

  private void enableNotifications(boolean enable) {
  	for (Sensor sensor : mEnabledSensors) {
  		UUID servUuid = sensor.getService();
  		UUID dataUuid = sensor.getData();
  		BluetoothGattService serv = mBtGatt.getService(servUuid);
  		BluetoothGattCharacteristic charac = serv.getCharacteristic(dataUuid);
  		
  		mBtLeService.setCharacteristicNotification(charac,enable);
			mBtLeService.waitIdle(GATT_TIMEOUT);
  	}
  }
  
  /* Calibrating the barometer includes
  * 
  * 1. Write calibration code to configuration characteristic. 
  * 2. Read calibration values from sensor, either with notifications or a normal read. 
  * 3. Use calibration values in formulas when interpreting sensor values.
  */
 private void calibrateBarometer() {
	 Log.i(TAG, "calibrateBarometer");
	 
	 UUID servUuid = Sensor.BAROMETER.getService();
	 UUID configUuid = Sensor.BAROMETER.getConfig();
	 BluetoothGattService serv = mBtGatt.getService(servUuid);
	 BluetoothGattCharacteristic config = serv.getCharacteristic(configUuid);

	 // Write the calibration code to the configuration registers
	 mBtLeService.writeCharacteristic(config,Sensor.CALIBRATE_SENSOR_CODE);
	 mBtLeService.waitIdle(GATT_TIMEOUT);
	 BluetoothGattCharacteristic calibrationCharacteristic = serv.getCharacteristic(SensorTag.UUID_BAR_CALI);
	 mBtLeService.readCharacteristic(calibrationCharacteristic);
	 mBtLeService.waitIdle(GATT_TIMEOUT);
 }

	void calibrateMagnetometer() {
		Log.d(TAG,"calibrateMagnetometer");
		MagnetometerCalibrationCoefficients.INSTANCE.val.x = 0.0;
		MagnetometerCalibrationCoefficients.INSTANCE.val.y = 0.0;
		MagnetometerCalibrationCoefficients.INSTANCE.val.z = 0.0;

		mMagCalibrateRequest = true;
	}
	
	void calibrateHeight() {
		mHeightCalibrateRequest = true;		
	}

  // Activity result handling
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
    case PREF_ACT_REQ:
    	mDeviceView.updateVisibility();
    	Toast.makeText(this, "Applying preferences", Toast.LENGTH_SHORT).show();
      if (!mIsReceiving) {
      	mIsReceiving = true;
      	registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
      }

    	updateSensorList();
      enableSensors(true);
      enableNotifications(true);
      break;
    case FWUPDATE_ACT_REQ:
    	// FW update cancelled so resume
      enableSensors(true);
      enableNotifications(true);
    	break;
    default:
      Log.e(TAG, "Unknown request code");
      break;
    }
  }

  private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
  	@Override
  	public void onReceive(Context context, Intent intent) {
  		final String action = intent.getAction();
  		int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS, BluetoothGatt.GATT_SUCCESS);

  		if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
  			if (status == BluetoothGatt.GATT_SUCCESS) {
  				displayServices();
  				checkOad();
  			} else {
  				Toast.makeText(getApplication(), "Service discovery failed", Toast.LENGTH_LONG).show();
  				return;
  			}
  		} else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
  			// Notification
  			byte  [] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
  			String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
  			onCharacteristicChanged(uuidStr, value);
  		} else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
  			// Data written
  			 System.out.println("广播接收器 Data Written.");
  			String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
  			onCharacteristicWrite(uuidStr,status);
  		} else if (BluetoothLeService.ACTION_DATA_READ.equals(action)) {
  			// Data read
  			System.out.println("广播接收器 Data Written.");
  			String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
  			byte  [] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
  			onCharacteristicsRead(uuidStr,value,status);
  		}

  		if (status != BluetoothGatt.GATT_SUCCESS) {
  			setError("GATT error code: " + status);
  		}
  	}
  };

	private void onCharacteristicWrite(String uuidStr, int status) {
		Log.d(TAG,"onCharacteristicWrite: " + uuidStr);
	  //回调函数  写入Characteristc 6 成功之后	
	  Write_Char_Success = true;	
	  System.out.println("DeviceActi-->onCharacteristicWrite.");	
	  System.out.println("uuidStr is :"+uuidStr);//Char6_UUID
	  if (status == BluetoothGatt.GATT_SUCCESS)
		  {System.out.println("Write to Char Success!");
		  readDatafromChar6();}
	  else
		  System.out.println("Write to Char Failed.");
  }
	private void readDatafromChar6()
	{//从Characteristic 6读出数据  远程读取
      mBtGatt.readCharacteristic(mChar); 
      System.out.println("远程读取readDatafromChar6.");
		
	}

	private void onCharacteristicChanged(String uuidStr, byte[] value) {
		if (mDeviceView != null) {
			if (mMagCalibrateRequest) {
				if (uuidStr.equals(SensorTag.UUID_MAG_DATA.toString())) {
					Point3D v = Sensor.MAGNETOMETER.convert(value);

					MagnetometerCalibrationCoefficients.INSTANCE.val = v;
					mMagCalibrateRequest = false;
					Toast.makeText(this, "Magnetometer calibrated", Toast.LENGTH_SHORT)
					    .show();
				}
			}

			if (mHeightCalibrateRequest) {
				if (uuidStr.equals(SensorTag.UUID_BAR_DATA.toString())) {
					Point3D v = Sensor.BAROMETER.convert(value);

					BarometerCalibrationCoefficients.INSTANCE.heightCalibration = v.x;
					mHeightCalibrateRequest = false;
					Toast.makeText(this, "Height measurement calibrated", Toast.LENGTH_SHORT)
					    .show();
				}
			}

			mDeviceView.onCharacteristicChanged(uuidStr, value);
		}
	}

	private void onCharacteristicsRead(String uuidStr, byte [] value, int status) {
		//张强 20140208
		System.out.println("DeviceAct-->onCharacteristicsRead.");
		System.out.println("uuidStr:"+uuidStr);
	    if (status == BluetoothGatt.GATT_SUCCESS)
		  {System.out.println("Read from Char Success!");
		  }
	   else
		  System.out.println("Read from Char Failed.");
	   System.out.printf("Read from char6 is:");
	   for(int i = 0; i < value.length; i++)
		     {System.out.printf("%d ", value[i]); }
	   System.out.println();
		   
		/*	  
		Log.i(TAG, "onCharacteristicsRead: " + uuidStr);
		if (uuidStr.equals(SensorTag.UUID_BAR_CALI.toString())) {
			Log.i(TAG, "CALIBRATED");
			// Barometer calibration values are read.
			List<Integer> cal = new ArrayList<Integer>();
			for (int offset = 0; offset < 8; offset += 2) {
				Integer lowerByte = (int) value[offset] & 0xFF; 
				Integer upperByte = (int) value[offset +1] & 0xFF;
				cal.add((upperByte << 8) + lowerByte);
			}

			for (int offset = 8; offset < 16; offset += 2) {
				Integer lowerByte = (int) value[offset] & 0xFF; 
				Integer upperByte = (int) value[offset +1]; 
				cal.add((upperByte << 8) + lowerByte);
			}

			BarometerCalibrationCoefficients.INSTANCE.barometerCalibrationCoefficients = cal;
		}*/
	}

}
