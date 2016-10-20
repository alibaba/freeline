package com.antfortune.freeline.gradle;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.resources.MonkeyPatcher;
import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.IDynamic;
import com.antfortune.freeline.util.ActivityManager;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

public class GradleDynamic implements IDynamic {

	private static final String TAG = "Freeline.GradleDynamic";

	private Application app;
	
	public GradleDynamic(Application context) {
		this.app = context;
	}

		@Override
		public boolean applyDynamicRes(HashMap<String, String> dynamicRes) {
			String dynamicResPath = dynamicRes.get(FreelineCore.DEFAULT_PACKAGE_ID);
			Log.i(TAG, "dynamicResPath: " + dynamicResPath);
			if (!TextUtils.isEmpty(dynamicResPath)) {
                Application realApplication = FreelineCore.getRealApplication();
				MonkeyPatcher.monkeyPatchApplication(app, app, realApplication, dynamicResPath);
				MonkeyPatcher.monkeyPatchExistingResources(app, dynamicResPath, Arrays.asList(ActivityManager.getAllActivities()));
				Log.i(TAG, "GradleDynamic apply dynamic resource successfully");
			}
		return true;
	}

	@Override
	public String getOriginResPath(String packageId) {
		File baseResFile = new File(FreelineCore.getDynamicInfoTempDir(), "full-res-pack.so");
		return baseResFile.getAbsolutePath();
	}

	@Override
	public void clearResourcesCache() {
		
	}

}
