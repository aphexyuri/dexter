package com.scopely.dcl;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.scopely.samplelib.interfaces.IToastHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "DCLProto";

    private final String TOAST_HELPER_CLASS = "com.scopely.samplelib.utils.ToastHelper";
    private final String TOAST_ENUMS = "com.scopely.samplelib.utils.ToastEnum";

    private static final String DEX_NAME = "app-classes-dex.jar";

    // Buffer size for file copying.  While 8kb is used in this sample, you
    // may want to tweak it based on actual size of the secondary dex file involved.
    private static final int BUF_SIZE = 8 * 1024;

    private IToastHelper toastHelper;
    private boolean invokeWithReflection = false;
    private Dexter toastHelperDexter;

    private Button mToastButton = null;
    private Switch mUseReflectionSwitch = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToastButton = (Button) findViewById(R.id.show_toasts);
        mUseReflectionSwitch = (Switch) findViewById(R.id.reflection_switch);
        mUseReflectionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                invokeWithReflection = isChecked;
            }
        });

        toastHelperDexter = new Dexter(DEX_NAME);
        toastHelperDexter.addDexLoadListerners(new Dexter.DexLoadListener() {
            @Override
            public void onLoadSuccess() {
                Log.d(TAG, "Dex load success");
//                toastHelperDexter.describeDexContents();
                mToastButton.setEnabled(true);
            }

            @Override
            public void onLoadFail() {
                Log.d(TAG, "Dex loadfailure");
            }
        });
        toastHelperDexter.init(getApplicationContext());

        mToastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get instance object
                Class toastHelperClass = toastHelperDexter.getClass(TOAST_HELPER_CLASS);
                Object toastHelperInstance = toastHelperDexter.getClassInstance(TOAST_HELPER_CLASS);

                toastHelperDexter.describeDexContents();

                try {
                    if(invokeWithReflection) {
                        Method m1 = toastHelperClass.getMethod("frenchToast", Context.class);
                        m1.invoke(toastHelperInstance, getApplicationContext());

                        Method m2 = toastHelperClass.getMethod("germanToast", Context.class);
                        m2.invoke(toastHelperInstance, getApplicationContext());

                        Method m3 = toastHelperClass.getMethod("italianToast", Context.class);
                        m3.invoke(toastHelperInstance, getApplicationContext());

                        Method sm1 = toastHelperClass.getMethod("frenchToastStatic", Context.class);
                        sm1.invoke(null, getApplicationContext());

                        //can only be used is all params are base classes >>>
                        List<Object> frenchToastStaticArgs = new ArrayList<Object>();
                        frenchToastStaticArgs.add(getApplicationContext());

                        List<Class> paramClasses = new ArrayList<Class>();
                        paramClasses.add(Context.class);

                        toastHelperDexter.callStatic(TOAST_HELPER_CLASS, "frenchToastStatic", paramClasses, frenchToastStaticArgs);
                        //can only be used is all params are base classes <<<
                    }
                    else {
                        Log.d(TAG, "Using interface...");
                        try {
                            // Cast the return object to the library interface so that the
                            // caller can directly invoke methods in the interface.
                            // Alternatively, the caller can invoke methods through reflection,
                            // which is more verbose and slow.
                            toastHelper = (IToastHelper) toastHelperClass.newInstance();

                            Log.d(TAG, "IToastHelper instance created");

                            // Display the toast!
                            toastHelper.frenchToast(getApplicationContext());
                            toastHelper.germanToast(getApplicationContext());
                            toastHelper.italianToast(getApplicationContext());
                        }
                        catch (IllegalAccessException e) {
                            Log.e(TAG, "IllegalAccessException " + e.toString());
                        }
                        catch (InstantiationException e) {
                            Log.e(TAG, "InstantiationException " + e.toString());
                        }
                    }

                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
