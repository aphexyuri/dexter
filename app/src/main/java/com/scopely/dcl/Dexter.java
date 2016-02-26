package com.scopely.dcl;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * Created by yurivisser on 16-02-08.
 */
public class Dexter {
    private static String TAG = "Dexter";

    private static final int BUF_SIZE = 8 * 1024;

    private Context mContext;
    private String mDexName;

    private DexLoadListener mDexLoadListener;

    private DexClassLoader cl;

    private boolean initialized;

    public Dexter(String dexName) {
        mDexName = dexName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void addDexLoadListerners(DexLoadListener loadListener) {
        mDexLoadListener = loadListener;
    }

    public void init(Context context) {
        mContext = context;
        if (!getDexInternalStoragePath().exists()) {
            Log.d(TAG, "Perform the file copying in an AsyncTask");
            // Perform the file copying in an AsyncTask.
            (new PrepareDexTask()).execute(getDexInternalStoragePath());
        } else {
            Log.w(TAG, "Internal dex storage path already exist");
            loadDexClasses();
        }
    }

    public Class<?> getClass(String className) {
        if(!initialized) {
            Log.w(TAG, "Dexter not initialized");
        }

        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Dexter.getClass: " + e.toString());
        }
        return null;
    }

    /**
     * Invokes a static method on provided class - Only use when all params are base classes
     *
     * @param className Full qualified class name e.g. com.scopely.utils.MyClassName
     * @param methodName The name of the method to call
     * @param methodParameters Params - Only use when all params are base classes
     * @param <T>
     * @return
     */
    public <T extends Object> Object callStatic(String className, String methodName, List<Class> klasses, List<T> methodParameters) {
        if(!initialized) {
            Log.w(TAG, "Dexter not initialized");
            return null;
        }

        try {
            Class klass = cl.loadClass(className);
            Method method = klass.getMethod(methodName, klasses.toArray(new Class[klasses.size()]));
            return method.invoke(null, methodParameters);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Dexter.callStatic: " + e.toString());
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Dexter.callStatic: " + e.toString());
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Dexter.callStatic: " + e.toString());
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Dexter.callStatic: " + e.toString());
        }
        return null;
    }

    /**
     * Invokes a method on provided instance - Only use when all params are base classes
     *
     * @param instance Class instance retrieved via getClass
     * @param className Full qualified class name e.g. com.scopely.utils.MyClassName
     * @param methodName The name of the method to call
     * @param methodParameters Params - Only use when all params are base classes
     * @param <T>
     * @return
     */
    public <T extends Object> Object call(Object instance, String className, String methodName, List<Class> klasses,  T... methodParameters) {
        if(!initialized) {
            Log.w(TAG, "Dexter not initialized");
            return null;
        }

        if(instance == null) {
            Log.w(TAG, "Dexter can not invoke the " + methodName + " on a null instance");
            return null;
        }

        try {
            Class klass = cl.loadClass(className);
            Method method = klass.getMethod(methodName, klasses.toArray(new Class[klasses.size()]));
            return method.invoke(instance, methodParameters);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Dexter.call: " + e.toString());
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Dexter.call: " + e.toString());
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Dexter.call: " + e.toString());
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Dexter.call: " + e.toString());
        }
        return null;
    }

    public Object getClassInstance(String className) {
        if(!initialized) {
            Log.w(TAG, "Dexter not initialized");
            return null;
        }

        try {
            Class klass = cl.loadClass(className);
            return (Object) klass.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Dexter.getClassInstance: " + e.toString());
        } catch (InstantiationException e) {
            Log.e(TAG, "Dexter.getClassInstance: " + e.toString());
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Dexter.getClassInstance: " + e.toString());
        }
        return null;
    }

    public void describeDexContents() {
        if(!initialized) {
            Log.w(TAG, "Dexter not initialized");
            return;
        }

        Log.d(TAG, "Describing dex file " + getDexInternalStoragePath().getPath() + ", " + getDexInternalStoragePath().getAbsolutePath());
        try {
            DexFile dx = DexFile.loadDex(
                    getDexInternalStoragePath().getAbsolutePath(),
                    File.createTempFile("opt", "dex", mContext.getCacheDir()).getPath(),
                    0);

            for(Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements();) {
                String className = classNames.nextElement();
                Log.d(TAG, "class in dex: " + className);
            }
        }
        catch (IOException e) {
            Log.w(TAG, "Error opening " + getDexInternalStoragePath().getAbsolutePath(), e);
        }
    }

    private void loadDexClasses() {
        cl = new DexClassLoader(getDexInternalStoragePath().getAbsolutePath(),
                getOptimizedDexOutputPath().getAbsolutePath(),
                null,
                mContext.getClassLoader());

        initialized = true;

        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mDexLoadListener.onLoadSuccess();
            }
        });
    }

    private File getDexInternalStoragePath() {
        return new File(mContext.getDir("dex", Context.MODE_PRIVATE), mDexName);
    }

    private File getOptimizedDexOutputPath() {
        return mContext.getDir("outdex", Context.MODE_PRIVATE);
    }

    private class PrepareDexTask extends AsyncTask<File, Void, Boolean> {
        @Override
        protected void onCancelled() {
            super.onCancelled();
            Log.d(TAG, "PrepareDexTask.onCancelled");
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            Log.d(TAG, "PrepareDexTask.onPostExecute, result: " + result);
        }

        @Override
        protected Boolean doInBackground(File... dexInternalStoragePaths) {
            Log.d(TAG, "PrepareDexTask.doInBackground");
            if(!prepareDex(dexInternalStoragePaths[0])) {
                Handler handler = new Handler(mContext.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDexLoadListener.onLoadFail();
                    }
                });
            }
            return null;
        }
    }

    private boolean prepareDex(File dexInternalStoragePath) {
        Log.d(TAG, "Preparing dex in " + dexInternalStoragePath.getPath());

        BufferedInputStream bis = null;
        OutputStream dexWriter = null;

        try {
            bis = new BufferedInputStream(mContext.getAssets().open(mDexName));
            dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
            byte[] buf = new byte[BUF_SIZE];
            int len;
            while((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
                dexWriter.write(buf, 0, len);
            }
            dexWriter.close();
            bis.close();

            loadDexClasses();
            return true;
        }
        catch (IOException e) {
            if (dexWriter != null) {
                try {
                    dexWriter.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            return false;
        }
    }

    public interface DexLoadListener {
        void onLoadSuccess();
        void onLoadFail();
    }
}
