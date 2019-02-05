package com.normsstuff.batterylevelwarning;

import java.util.Date;

import android.app.Activity;
import android.os.Bundle;

public class GetTimesActivity extends Activity {
	//-------------------------------------------------------------------------
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		System.out.println("GTA onCreate() at "+ new Date());
	}
}
