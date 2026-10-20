package com.mrdany.torboxtv;

import android.app.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;

final class TorBoxSetup {
    private static boolean open;
    static final String GROUPS = "RODubbed,RoDub,playWEB,playBD,playHD,SPWEB,VELUN,BBAD,KBT,SPHD,CineTeam,Empire,CRiMSON,FLX,SZ,iNT,NTb,CtrlHD";
    static void show() {
        final Activity a = TorBoxPlugin.activity.get();
        if (a == null || a.isFinishing()) return;
        a.runOnUiThread(() -> {
            if (open) return;
            open = true;
            LinearLayout layout = new LinearLayout(a); layout.setOrientation(1); layout.setPadding(28,12,28,12);
            EditText key = new EditText(a); key.setSingleLine(true);
            key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            key.setHint("Enter TorBox API key privately here");
            EditText groups = new EditText(a); groups.setHint("Trusted release groups, comma separated");
            groups.setText(TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).getString("groups",GROUPS));
            TextView status = new TextView(a);
            status.setText("Searches Library plus public torrent indexes. Only cache-confirmed exact files are registered with TorBox; these may appear in your Library. No uncached downloads. Available Matroska audio tags take priority; trusted release groups provide a Possible RO Dub fallback when metadata is missing.");
            layout.addView(status); layout.addView(key); layout.addView(groups);
            AlertDialog dialog = new AlertDialog.Builder(a).setTitle("TorBox Library TV — private setup")
                .setView(layout).setPositiveButton("Save and test",null).setNegativeButton("Cancel",null)
                .setNeutralButton("Forget key",(d,w)->new PrivateKeyStore(a).clear()).create();
            dialog.setOnDismissListener(d->{key.setText("");open=false;});
            dialog.show(); dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(-1).setOnClickListener(v->{
                final String entered=key.getText().toString().trim();
                final String trusted=groups.getText().toString().trim();
                if(entered.isEmpty()){status.setText("Enter your key above. It will not be shown or logged.");return;}
                dialog.getButton(-1).setEnabled(false); status.setText("Checking TorBox Library…");
                Thread worker=new Thread(()->{
                    try {
                        JSONArray items=new TorBoxClient(entered).page(0,100);
                        new PrivateKeyStore(a).save(entered);
                        if(!TorBoxPlugin.app.getSharedPreferences("torbox_tv_options",0).edit().putString("groups",trusted).commit())throw new Exception();
                        a.runOnUiThread(()->{key.setText("");status.setText("Connected. Key saved encrypted on this device. First Library page: "+items.length()+" items. Run Test again, or open a title, to search.");dialog.getButton(-1).setEnabled(true);});
                    } catch(Exception e) {
                        a.runOnUiThread(()->{key.setText("");status.setText("Connection failed. Check your key and TorBox availability. No new key was saved unless validation succeeded.");dialog.getButton(-1).setEnabled(true);});
                    }
                },"TorBox-private-setup");worker.setDaemon(true);worker.start();
            });
        });
    }
}
