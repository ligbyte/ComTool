package com.lime.bean;

import java.text.SimpleDateFormat;

/**
 * @author benjaminwan
 *串口数据
 */
public class ComBean {
		public static volatile boolean showMillis = false;
		public byte[] bRec=null;
		public String sRecTime="";
		public long lRecTime=0;
		public String sComPort="";
		public ComBean(String sPort,byte[] buffer,int size){
			sComPort=sPort;
			bRec=new byte[size];
			for (int i = 0; i < size; i++)
			{
				bRec[i]=buffer[i];
			}
			lRecTime = System.currentTimeMillis();
			String pattern = showMillis ? "hh:mm:ss.SSS" : "hh:mm:ss";
			SimpleDateFormat sDateFormat = new SimpleDateFormat(pattern);
			sRecTime = sDateFormat.format(new java.util.Date(lRecTime));
		}
}