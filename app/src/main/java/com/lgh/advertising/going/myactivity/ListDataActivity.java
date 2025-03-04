package com.lgh.advertising.going.myactivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.advertising.going.BuildConfig;
import com.lgh.advertising.going.databinding.ActivityListDataBinding;
import com.lgh.advertising.going.databinding.ViewEditFileNameBinding;
import com.lgh.advertising.going.databinding.ViewItemAppBinding;
import com.lgh.advertising.going.databinding.ViewOnOffWarningBinding;
import com.lgh.advertising.going.mybean.AppDescribe;
import com.lgh.advertising.going.mybean.Regulation;
import com.lgh.advertising.going.mybean.RegulationExport;
import com.lgh.advertising.going.mybean.Widget;
import com.lgh.advertising.going.myclass.DataDao;
import com.lgh.advertising.going.myclass.MyApplication;
import com.lgh.advertising.going.myfunction.MyUtils;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ListDataActivity extends BaseActivity {

    private final MyAdapter myAdapter = new MyAdapter();
    private final List<AppDescribe> appDescribeList = new ArrayList<>();
    private final List<AppDescribeItem> appDescribeItemList = new ArrayList<>();
    private final List<AppDescribeItem> appDescribeItemFilterList = new ArrayList<>();
    private final Set<String> pkgSuggestNotOnList = new HashSet<>();
    private final List<AppDescribe> regulationExportList = new ArrayList<>();
    private ActivityListDataBinding listDataBinding;
    private Context context;
    private DataDao dataDao;
    private PackageManager packageManager;
    private MyUtils myUtils;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listDataBinding = ActivityListDataBinding.inflate(getLayoutInflater());
        listDataBinding.llSelect.setVisibility(View.GONE);
        setContentView(listDataBinding.getRoot());
        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        packageManager = getPackageManager();
        myUtils = MyApplication.myUtils;

        if (!myUtils.isServiceRunning()) {
            Toast.makeText(context, "无障碍服务未开启", Toast.LENGTH_SHORT).show();
        }

        Set<String> pkgSysSet = packageManager.
                getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)
                .stream().map(e -> e.packageName)
                .collect(Collectors.toSet());
        Set<String> pkgInputMethodSet = getSystemService(InputMethodManager.class)
                .getInputMethodList()
                .stream()
                .map(InputMethodInfo::getPackageName)
                .collect(Collectors.toSet());
        Set<String> pkgHasHomeSet = packageManager
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .stream()
                .map(e -> e.activityInfo.packageName)
                .collect(Collectors.toSet());
        pkgSuggestNotOnList.addAll(pkgSysSet);
        pkgSuggestNotOnList.addAll(pkgInputMethodSet);
        pkgSuggestNotOnList.addAll(pkgHasHomeSet);

        listDataBinding.recyclerView.setAdapter(myAdapter);

        List<String> searchKeyword = new ArrayList<>();
        searchKeyword.add("@开启");
        searchKeyword.add("@关闭");
        searchKeyword.add("@已创建规则");
        searchKeyword.add("@未创建规则");
        searchKeyword.add("@系统应用");
        searchKeyword.add("@非系统应用");
        searchKeyword.add("@非必要不开启应用");
        listDataBinding.searchBox.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, searchKeyword));
        final Filter filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                appDescribeItemFilterList.clear();
                if (constraint.equals("@开启")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        if (e.appDescribe.onOff) {
                            appDescribeItemFilterList.add(e);
                        }
                    }
                    return null;
                }
                if (constraint.equals("@关闭")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        if (!e.appDescribe.onOff) {
                            appDescribeItemFilterList.add(e);
                        }
                    }
                    return null;
                }
                if (constraint.equals("@已创建规则")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        if (!e.appDescribe.coordinateMap.isEmpty() || !e.appDescribe.widgetSetMap.isEmpty()) {
                            appDescribeItemFilterList.add(e);
                        }
                    }
                    return null;
                }
                if (constraint.equals("@未创建规则")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        if (e.appDescribe.coordinateMap.isEmpty() && e.appDescribe.widgetSetMap.isEmpty()) {
                            appDescribeItemFilterList.add(e);
                        }
                    }
                    return null;
                }
                if (constraint.equals("@系统应用")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        try {
                            if ((packageManager.getApplicationInfo(e.appDescribe.appPackage, PackageManager.GET_META_DATA).flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM) {
                                appDescribeItemFilterList.add(e);
                            }
                        } catch (PackageManager.NameNotFoundException ex) {
                            // ex.printStackTrace();
                        }
                    }
                    return null;
                }
                if (constraint.equals("@非系统应用")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        try {
                            if ((packageManager.getApplicationInfo(e.appDescribe.appPackage, PackageManager.GET_META_DATA).flags & ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM) {
                                appDescribeItemFilterList.add(e);
                            }
                        } catch (PackageManager.NameNotFoundException ex) {
                            // ex.printStackTrace();
                        }
                    }
                    return null;
                }
                if (constraint.equals("@非必要不开启应用")) {
                    for (AppDescribeItem e : appDescribeItemList) {
                        if (pkgSuggestNotOnList.contains(e.appDescribe.appPackage)) {
                            appDescribeItemFilterList.add(e);
                        }
                    }
                    return null;
                }

                for (AppDescribeItem e : appDescribeItemList) {
                    String str = constraint.toString().toLowerCase();
                    if (e.appDescribe.appName.toLowerCase().contains(str) || e.appDescribe.appPackage.contains(str)) {
                        appDescribeItemFilterList.add(e);
                    }
                }
                return null;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                myAdapter.notifyDataSetChanged();
            }
        };
        listDataBinding.searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter.filter(s.toString().trim());
            }
        });

        listDataBinding.recyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    v.requestFocus();
                }
                return false;
            }
        });

        listDataBinding.btCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                regulationExportList.clear();
                appDescribeItemList.forEach(e -> e.isSelected = false);
                listDataBinding.cbSelectAll.setChecked(false);
                listDataBinding.llSelect.setVisibility(View.GONE);
                myAdapter.notifyDataSetChanged();
            }
        });

        listDataBinding.btExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RegulationExport regulationExport = new RegulationExport();
                regulationExport.fingerPrint = Build.FINGERPRINT;
                regulationExport.displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(regulationExport.displayMetrics);
                for (AppDescribe appdescribe : regulationExportList) {
                    Regulation regulation = new Regulation();
                    regulation.appDescribe = appdescribe;
                    regulation.autoFinder = appdescribe.autoFinder;
                    regulation.coordinateList.addAll(appdescribe.coordinateMap.values());
                    for (Set<Widget> widgetSet : appdescribe.widgetSetMap.values()) {
                        regulation.widgetList.addAll(widgetSet);
                    }
                    regulationExport.regulationList.add(regulation);
                }
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String strRegulation = '"' + RegulationExport.class.getSimpleName() + '"' + ": " + gson.toJson(regulationExport);
                showEditShareFileNameDialog(strRegulation);
            }
        });

        listDataBinding.cbSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                regulationExportList.clear();
                for (AppDescribeItem e : appDescribeItemList) {
                    e.isSelected = listDataBinding.cbSelectAll.isChecked();
                    boolean b = e.isSelected && regulationExportList.add(e.appDescribe);
                }
                listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", regulationExportList.size()));
                myAdapter.notifyDataSetChanged();
            }
        });

        Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<Boolean> emitter) throws Throwable {
                appDescribeList.addAll(dataDao.getAllAppDescribes());
                for (AppDescribe e : appDescribeList) {
                    e.getOtherFieldsFromDatabase(dataDao);
                }
                appDescribeList.sort(new Comparator<AppDescribe>() {
                    @Override
                    public int compare(AppDescribe o1, AppDescribe o2) {
                        return Collator.getInstance(Locale.CHINESE).compare(o1.appName, o2.appName);
                    }
                });
                ListIterator<AppDescribe> iterator = appDescribeList.listIterator();
                while (iterator.hasNext()) {
                    try {
                        AppDescribe e = iterator.next();
                        Drawable icon = packageManager.getApplicationIcon(e.appPackage);
                        appDescribeItemList.add(new AppDescribeItem(e, icon));
                    } catch (PackageManager.NameNotFoundException e) {
                        iterator.remove();
                        // e.printStackTrace();
                    }
                }
                appDescribeItemFilterList.addAll(appDescribeItemList);
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Boolean>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                listDataBinding.recyclerView.setVisibility(View.INVISIBLE);
                listDataBinding.searchBox.setVisibility(View.INVISIBLE);
                listDataBinding.progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNext(@NonNull Boolean aBoolean) {
            }

            @Override
            public void onError(@NonNull Throwable e) {
                Toast.makeText(context, "出现错误", Toast.LENGTH_SHORT).show();
                listDataBinding.recyclerView.setVisibility(View.VISIBLE);
                listDataBinding.searchBox.setVisibility(View.VISIBLE);
                listDataBinding.progress.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onComplete() {
                myAdapter.notifyDataSetChanged();
                listDataBinding.recyclerView.setVisibility(View.VISIBLE);
                listDataBinding.searchBox.setVisibility(View.VISIBLE);
                listDataBinding.progress.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        myAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(listDataBinding.searchBox.getText())) {
            listDataBinding.searchBox.setText(null);
            return;
        }
        if (listDataBinding.llSelect.getVisibility() == View.VISIBLE) {
            listDataBinding.btCancel.callOnClick();
            return;
        }
        super.onBackPressed();
    }

    private void showEditShareFileNameDialog(String strRegulation) {
        ViewEditFileNameBinding binding = ViewEditFileNameBinding.inflate(getLayoutInflater());
        binding.fileName.setHint(DigestUtils.md5Hex(strRegulation));
        new AlertDialog.Builder(ListDataActivity.this)
                .setView(binding.getRoot())
                .setCancelable(false)
                .setTitle("编辑文件名称")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            FileUtils.cleanDirectory(getCacheDir());
                            String fileName = binding.fileName.getText().toString();
                            File file = new File(getCacheDir(), (fileName.isEmpty() ? binding.fileName.getHint() : fileName) + ".txt");
                            FileUtils.writeStringToFile(file, strRegulation, StandardCharsets.UTF_8);
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
                            intent.setDataAndType(uri, getContentResolver().getType(uri));
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            intent.setClipData(new ClipData(ClipData.newUri(getContentResolver(), "regulation", uri)));
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(intent, "保存"));
                        } catch (IOException ex) {
                            Toast.makeText(context, "生成规则文件时发生错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).create().show();
    }

    static class AppDescribeItem {
        AppDescribe appDescribe;
        Drawable icon;
        boolean isSelected;

        public AppDescribeItem(AppDescribe appDescribe, Drawable icon) {
            this.appDescribe = appDescribe;
            this.icon = icon;
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {

        @Override
        @androidx.annotation.NonNull
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            ViewItemAppBinding itemAppBinding = ViewItemAppBinding.inflate(getLayoutInflater(), parent, false);
            return new ViewHolder(itemAppBinding);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            final AppDescribeItem item = appDescribeItemFilterList.get(position);
            holder.itemAppBinding.name.setText(item.appDescribe.appName);
            holder.itemAppBinding.pkg.setText(item.appDescribe.appPackage);
            holder.itemAppBinding.img.setImageDrawable(item.icon);
            holder.itemAppBinding.onOff.setChecked(item.appDescribe.onOff);
            holder.itemAppBinding.cbSelect.setChecked(item.isSelected);
            holder.itemAppBinding.cbSelect.setVisibility(listDataBinding.llSelect.getVisibility());
        }

        @Override
        public int getItemCount() {
            return appDescribeItemFilterList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewItemAppBinding itemAppBinding;

            public ViewHolder(ViewItemAppBinding binding) {
                super(binding.getRoot());
                itemAppBinding = binding;
                itemAppBinding.onOff.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        boolean isChecked = itemAppBinding.onOff.isChecked();
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                item.appDescribe.onOff = isChecked;
                                item.appDescribe.autoFinderOnOFF = isChecked;
                                item.appDescribe.widgetOnOff = isChecked;
                                item.appDescribe.coordinateOnOff = isChecked;
                                dataDao.updateAppDescribe(item.appDescribe);
                                myUtils.requestUpdateAppDescribe(item.appDescribe.appPackage);
                            }
                        };
                        if (isChecked && pkgSuggestNotOnList.contains(item.appDescribe.appPackage)) {
                            itemAppBinding.onOff.setChecked(false);
                            View view = ViewOnOffWarningBinding.inflate(getLayoutInflater()).getRoot();
                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ListDataActivity.this);
                            alertDialogBuilder.setView(view);
                            alertDialogBuilder.setNegativeButton("取消", null);
                            alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    runnable.run();
                                    itemAppBinding.onOff.setChecked(true);
                                }
                            });
                            AlertDialog dialog = alertDialogBuilder.create();
                            dialog.show();
                        } else {
                            runnable.run();
                        }
                    }
                });
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        EditDataActivity.appDescribe = item.appDescribe;
                        startActivity(new Intent(context, EditDataActivity.class));
                    }
                });

                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        item.isSelected = true;
                        regulationExportList.add(item.appDescribe);
                        listDataBinding.llSelect.setVisibility(View.VISIBLE);
                        listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", regulationExportList.size()));
                        notifyDataSetChanged();
                        return true;
                    }
                });

                itemAppBinding.cbSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppDescribeItem item = appDescribeItemFilterList.get(getAdapterPosition());
                        item.isSelected = itemAppBinding.cbSelect.isChecked();
                        boolean b = item.isSelected ? regulationExportList.add(item.appDescribe) : regulationExportList.remove(item.appDescribe);
                        listDataBinding.tvSelectedNum.setText(String.format(Locale.ROOT, "已选%s项", regulationExportList.size()));
                        listDataBinding.cbSelectAll.setChecked(regulationExportList.size() == appDescribeItemList.size());
                    }
                });
            }
        }
    }
}