package com.virtualbeehive.expensekeeper;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.os.ParcelFileDescriptor;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.content.res.ColorStateList;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.Scope;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class MainActivity extends Activity {
    final int HONEY = Color.rgb(246, 184, 52), HONEY_LIGHT = Color.rgb(255, 212, 96);
    final int BG = Color.rgb(12, 10, 7), CARD = Color.rgb(29, 25, 19), FIELD = Color.rgb(20, 18, 14), BORDER = Color.rgb(74, 60, 38);
    final int MUTED = Color.rgb(188, 178, 160), WHITE = Color.rgb(250, 248, 242);
    final String WEB_CLIENT_ID = "418570478857-n8088u5v7nsa45rffu2cpbnlg8o89rmp.apps.googleusercontent.com";
    final String DRIVE_FOLDER_ID = "1nQHsHI4KKbZAuMMvbE4z53E2O23fOigr";
    final String SCOPE_DRIVE = "https://www.googleapis.com/auth/drive.file";
    final String SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets";

    LinearLayout body;
    SharedPreferences sp;
    String activeTab = "Dashboard";
    Uri pendingCameraUri;
    Uri currentReceiptUri;
    GoogleSignInClient signInClient;
    ImageView profileImageView;
    Bitmap profileBitmap;

    String[] categories = {"Advertising & Marketing", "AI Tools Subscriptions", "Software & Subscriptions", "Website / App Development", "Office Supplies", "Computer / Equipment", "Phone & Internet", "Travel", "Business Meals", "Vehicle / Mileage", "Professional Fees", "Bank / Payment Processing Fees", "Rent / Office / Coworking", "Insurance", "Postage & Shipping", "Training / Education", "Licenses / Permits / Filing Fees", "Contract Labor", "Payroll / Wages", "Taxes", "Repairs & Maintenance", "Other Business Expense"};

    EditText addAmount, addTitle, addDesc, addVendor, addDate;
    Spinner addCat;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("vb_expense_keeper", 0);
        if (Build.VERSION.SDK_INT >= 21) { getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SCOPE_DRIVE), new Scope(SCOPE_SHEETS))
                .requestIdToken(WEB_CLIENT_ID)
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);
        generateDueRecurringExpenses();
        showDashboard();
    }

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}    
    int statusBar(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return id>0?getResources().getDimensionPixelSize(id):dp(24);}    
    GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable outlined(int color,int radius,int stroke){GradientDrawable g=round(color,radius);g.setStroke(dp(1),stroke);return g;}
    TextView tv(String s,int size,int color,int style){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,style);t.setLineSpacing(dp(2),1);return t;}
    void add(LinearLayout p,View c,int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h);lp.setMargins(dp(l),dp(t),dp(r),dp(b));p.addView(c,lp);}    
    TextView primaryButton(String s){TextView b=tv(s,15,Color.rgb(17,13,7),Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(dp(14),dp(13),dp(14),dp(13));b.setBackground(round(HONEY,16));return b;}
    TextView secondaryButton(String s){TextView b=tv(s,14,HONEY_LIGHT,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(outlined(Color.rgb(26,22,16),14,BORDER));return b;}
    EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setTextColor(WHITE);e.setHintTextColor(Color.rgb(150,141,125));e.setPadding(dp(14),0,dp(14),0);e.setMinHeight(dp(52));e.setSingleLine(true);e.setBackground(outlined(FIELD,14,BORDER));if(Build.VERSION.SDK_INT>=21)e.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));return e;}
    Spinner spinner(String[] items){ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){@Override public View getView(int p,View v,ViewGroup par){TextView x=(TextView)super.getView(p,v,par);x.setTextColor(WHITE);x.setTextSize(16);x.setPadding(dp(14),0,dp(14),0);return x;}@Override public View getDropDownView(int p,View v,ViewGroup par){TextView x=(TextView)super.getDropDownView(p,v,par);x.setTextColor(Color.rgb(20,16,10));x.setTextSize(16);x.setPadding(dp(16),dp(12),dp(16),dp(12));return x;}};a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);Spinner s=new Spinner(this);s.setAdapter(a);s.setBackground(outlined(FIELD,14,BORDER));return s;}
    void label(LinearLayout p,String s){add(p,tv(s,13,HONEY_LIGHT,Typeface.BOLD),-1,-2,2,12,0,5);}    

    void base(String screen){activeTab=screen;LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(14),statusBar()+dp(8),dp(14),dp(8));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(10),dp(10),dp(10));header.setBackground(outlined(Color.rgb(20,17,13),20,Color.rgb(54,43,28)));
        ImageView logo=new ImageView(this);logo.setImageResource(getResources().getIdentifier("vb_logo","drawable",getPackageName()));logo.setScaleType(ImageView.ScaleType.FIT_CENTER);add(header,logo,dp(38),dp(38),0,0,10,0);
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);titleBox.addView(tv("VB Expense Keeper",21,HONEY,Typeface.BOLD));titleBox.addView(tv("Virtual Beehive Inc. private bookkeeping",12,MUTED,Typeface.NORMAL));header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=secondaryButton("☰");menu.setTextSize(22);menu.setOnClickListener(v->showMenu());header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));add(root,header,-1,-2,0,0,0,12);
        add(root,tv(screen,30,WHITE,Typeface.BOLD),-1,-2,2,0,0,8);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,dp(12));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add(root,bottomNav(),-1,dp(70),0,6,0,0);setContentView(root,new ViewGroup.LayoutParams(-1,-1));}
    LinearLayout bottomNav(){LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(6),dp(6),dp(6),dp(6));nav.setBackground(outlined(Color.rgb(21,18,13),22,Color.rgb(58,47,31)));String[] screens={"Home","Add Expense","Records","Profile"};String[] labels={"Home","Add","Records","Profile"};for(int i=0;i<screens.length;i++){String scr=screens[i];TextView tab=tv(labels[i],12,scr.equals(activeTab)?Color.rgb(18,13,6):MUTED,Typeface.BOLD);tab.setGravity(Gravity.CENTER);tab.setBackground(scr.equals(activeTab)?round(HONEY,16):round(Color.TRANSPARENT,16));tab.setOnClickListener(v->{if(scr.equals("Home"))showDashboard();else if(scr.equals("Add Expense"))showAdd();else if(scr.equals("Records"))showRecords();else showProfile();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(3),0,dp(3),0);nav.addView(tab,lp);}return nav;}
    LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(outlined(CARD,20,Color.rgb(60,48,32)));c.addView(tv(title,18,HONEY,Typeface.BOLD));add(body,c,-1,-2,0,6,0,10);return c;}
    void showMenu(){String email=sp.getString("googleEmail","");String[] items={email.isEmpty()?"Connect Google Account":"Reconnect Google Account","Retry Pending Sync","Reset Sheet Link and Retry","Sign Out Google","Profile Settings","App Info"};new AlertDialog.Builder(this).setTitle("VB Expense Keeper Menu").setItems(items,(d,which)->{if(which==0){startActivityForResult(signInClient.getSignInIntent(),900);}else if(which==1){retryPendingSync();}else if(which==2){clearAllSheetLinks();Toast.makeText(this,"Sheet links cleared. Open Records and tap Retry Pending Sync.",Toast.LENGTH_LONG).show();}else if(which==3){signInClient.signOut();sp.edit().remove("googleEmail").remove("lastSyncError").apply();Toast.makeText(this,"Google signed out",Toast.LENGTH_LONG).show();showDashboard();}else if(which==4){showProfile();}else{new AlertDialog.Builder(this).setTitle("About").setMessage("VB Expense Keeper\nPrivate bookkeeping app for Virtual Beehive Inc.\n\nUse Records for Google sync controls. Use Add for new expenses.").setPositiveButton("OK",null).show();}}).show();}
    void clearAllSheetLinks(){SharedPreferences.Editor e=sp.edit();for(int y=2020;y<=2040;y++){e.remove("sheet_"+y);e.remove("receiptFolder_"+y);}e.remove("lastSyncError").apply();}
    JSONArray getRecords(){try{return new JSONArray(sp.getString("records","[]"));}catch(Exception e){return new JSONArray();}}
    String[] getCategories(){ArrayList<String> list=new ArrayList<>();for(String c:categories)list.add(c);try{JSONArray arr=new JSONArray(sp.getString("customCategories","[]"));for(int i=0;i<arr.length();i++){String v=arr.optString(i).trim();if(!v.isEmpty()&&!list.contains(v))list.add(v);}}catch(Exception ignored){}return list.toArray(new String[0]);}
    void addCustomCategory(Spinner spin){final EditText e=input("New category name");new AlertDialog.Builder(this).setTitle("Add Expense Category").setMessage("Enter a new company expense category. It will be saved on this phone and added to the list.").setView(e).setPositiveButton("Add",(d,w)->{String v=e.getText().toString().trim();if(v.isEmpty())return;try{JSONArray arr=new JSONArray(sp.getString("customCategories","[]"));boolean exists=false;for(int i=0;i<arr.length();i++)if(v.equalsIgnoreCase(arr.optString(i))){exists=true;break;}if(!exists)arr.put(v);sp.edit().putString("customCategories",arr.toString()).apply();String[] all=getCategories();spin.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,all){@Override public View getView(int p,View v2,ViewGroup par){TextView x=(TextView)super.getView(p,v2,par);x.setTextColor(WHITE);x.setTextSize(16);x.setPadding(dp(14),0,dp(14),0);return x;}@Override public View getDropDownView(int p,View v2,ViewGroup par){TextView x=(TextView)super.getDropDownView(p,v2,par);x.setTextColor(Color.rgb(20,16,10));x.setTextSize(16);x.setPadding(dp(16),dp(12),dp(16),dp(12));return x;}});for(int i=0;i<all.length;i++)if(all[i].equals(v)){spin.setSelection(i);break;}Toast.makeText(this,"Category added",Toast.LENGTH_LONG).show();}catch(Exception ex){Toast.makeText(this,"Could not add category: "+ex.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancel",null).show();}

    public void showDashboard(){base("Home");
        LinearLayout hero=card("");
        hero.removeAllViews();
        String photo=sp.getString("profilePhoto","");
        if(!photo.isEmpty()){
            ImageView pv=new ImageView(this);
            Bitmap bm=BitmapFactory.decodeFile(photo);
            if(bm!=null)pv.setImageBitmap(bm);
            pv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pv.setBackground(outlined(Color.rgb(22,18,14),24,HONEY));
            add(hero,pv,-1,dp(260),0,6,0,14);
        }else{
            TextView ph=secondaryButton("Add your profile picture in Profile tab");
            ph.setOnClickListener(v->showProfile());
            add(hero,ph,-1,dp(90),0,8,0,14);
        }
        TextView nm=tv(sp.getString("name","Daniel Pirooz"),28,WHITE,Typeface.BOLD);
        nm.setGravity(Gravity.CENTER);
        add(hero,nm,-1,-2,0,4,0,0);
        TextView ps=tv(sp.getString("position","CEO / Founder"),18,HONEY_LIGHT,Typeface.BOLD);
        ps.setGravity(Gravity.CENTER);
        add(hero,ps,-1,-2,0,3,0,18);
        Bitmap qr=makeQr(vcard(sp.getString("name","Daniel Pirooz"),sp.getString("position","CEO / Founder"),sp.getString("phone",""),sp.getString("email","danny@virtualbeehiveinc.com")),dp(230));
        if(qr!=null){
            ImageView qv=new ImageView(this);
            qv.setImageBitmap(qr);
            qv.setBackgroundColor(Color.WHITE);
            qv.setPadding(dp(10),dp(10),dp(10),dp(10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(250),dp(250));
            lp.gravity=Gravity.CENTER_HORIZONTAL;
            lp.setMargins(0,0,0,dp(4));
            hero.addView(qv,lp);
        }
    }

    public void showAdd(){base("Add Expense");LinearLayout c=card("New Expense");c.addView(tv("Take or upload a receipt. The app will try to read date, amount, vendor and title. Review before saving.",14,MUTED,Typeface.NORMAL));
        label(c,"Expense Date");addDate=input("YYYY-MM-DD");addDate.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));add(c,addDate,-1,-2,0,0,0,2);
        label(c,"Category");addCat=spinner(getCategories());add(c,addCat,-1,dp(52),0,0,0,4);TextView addCategory=secondaryButton("+ Add New Category");addCategory.setOnClickListener(v->addCustomCategory(addCat));add(c,addCategory,-1,-2,0,0,0,2);
        label(c,"Frequency");Spinner freq=spinner(new String[]{"One-time","Monthly recurring","Yearly recurring"});add(c,freq,-1,dp(52),0,0,0,2);
        label(c,"Amount");addAmount=input("49.99");add(c,addAmount,-1,-2,0,0,0,2);
        label(c,"Title");addTitle=input("Expense title");add(c,addTitle,-1,-2,0,0,0,2);
        label(c,"Description / Business Purpose");addDesc=input("Why this was for business");addDesc.setSingleLine(false);addDesc.setMinLines(2);add(c,addDesc,-1,dp(76),0,0,0,2);
        label(c,"Vendor / Store");addVendor=input("Vendor name");add(c,addVendor,-1,-2,0,0,0,12);
        TextView camera=secondaryButton("Take Receipt Photo");camera.setOnClickListener(v->takeReceiptPhoto());add(c,camera,-1,-2,0,2,0,8);
        TextView upload=secondaryButton("Upload Existing Receipt or PDF");upload.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(Intent.createChooser(i,"Select receipt image or PDF"),77);});add(c,upload,-1,-2,0,0,0,8);
        TextView save=primaryButton("Record Expense + Sync Sheet");save.setOnClickListener(v->saveExpense(addCat,freq,addAmount,addTitle,addDesc,addVendor,addDate));add(c,save,-1,-2,0,6,0,0);
    }

    void takeReceiptPhoto(){try{if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},44);return;}File f=new File(getExternalCacheDir(),"receipt_"+System.currentTimeMillis()+".jpg");pendingCameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,pendingCameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,78);}catch(Exception e){Toast.makeText(this,"Camera error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;try{
            if(request==55 && data!=null){Uri u=data.getData();String path=copyProfilePhoto(u);sp.edit().putString("profilePhoto",path).apply();Toast.makeText(this,"Profile picture saved",Toast.LENGTH_LONG).show();showProfile();}
            else if(request==77 && data!=null){currentReceiptUri=data.getData();try{getContentResolver().takePersistableUriPermission(currentReceiptUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}scanReceipt(currentReceiptUri);}
            else if(request==78){currentReceiptUri=pendingCameraUri;scanReceipt(currentReceiptUri);}
            else if(request==900){GoogleSignInAccount acct=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(Exception.class);sp.edit().putString("googleEmail",acct.getEmail()==null?"Connected":acct.getEmail()).remove("lastSyncError").apply();Toast.makeText(this,"Google connected",Toast.LENGTH_LONG).show();retryPendingSync();showDashboard();}
            else if(request==901){Toast.makeText(this,"Google permission updated. Retrying pending sync.",Toast.LENGTH_LONG).show();retryPendingSync();showDashboard();}
        }catch(Exception e){Toast.makeText(this,"Result error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    String copyProfilePhoto(Uri uri)throws Exception{File out=new File(getFilesDir(),"profile_photo.jpg");InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new Exception("Could not open image");OutputStream os=new FileOutputStream(out);byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)os.write(buf,0,n);in.close();os.close();sp.edit().putFloat("profileZoom",1.0f).putFloat("profileOffsetY",0f).apply();return out.getAbsolutePath();}
    String copyReceiptToPrivate(Uri uri,String ref)throws Exception{File dir=new File(getFilesDir(),"receipts");if(!dir.exists())dir.mkdirs();String ext=receiptExtension(uri);File out=new File(dir,ref.replace(":","-")+ext);InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new Exception("Could not open receipt");OutputStream os=new FileOutputStream(out);byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)os.write(buf,0,n);in.close();os.close();return out.getAbsolutePath();}
    String receiptMime(Uri uri){String m=getContentResolver().getType(uri);return m==null?"":m.toLowerCase(Locale.US);}    
    boolean isPdf(Uri uri){String m=receiptMime(uri);String u=uri.toString().toLowerCase(Locale.US);return m.contains("pdf")||u.endsWith(".pdf");}
    String receiptExtension(Uri uri){if(isPdf(uri))return ".pdf";String m=receiptMime(uri);String ext=MimeTypeMap.getSingleton().getExtensionFromMimeType(m);if(ext!=null&&!ext.trim().isEmpty())return "."+ext;return ".jpg";}
    String mimeForFile(File f){String n=f.getName().toLowerCase(Locale.US);if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".png"))return "image/png";if(n.endsWith(".webp"))return "image/webp";return "image/jpeg";}

    void scanReceipt(Uri uri){Toast.makeText(this,isPdf(uri)?"Reading first page of PDF receipt...":"Reading receipt...",Toast.LENGTH_SHORT).show();try{InputImage image;if(isPdf(uri)){Bitmap page=renderPdfFirstPage(uri);if(page==null)throw new Exception("Could not render PDF page");image=InputImage.fromBitmap(page,0);}else{image=InputImage.fromFilePath(this,uri);}TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener(text->{String raw=text.getText();fillFromReceipt(raw);sp.edit().putString("lastOcr",raw).apply();Toast.makeText(this,"Receipt read. Please review fields.",Toast.LENGTH_LONG).show();}).addOnFailureListener(e->Toast.makeText(this,"Could not read receipt. Please enter manually.",Toast.LENGTH_LONG).show());}catch(Exception e){Toast.makeText(this,"Receipt scan error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    Bitmap renderPdfFirstPage(Uri uri)throws Exception{ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(uri,"r");if(pfd==null)return null;PdfRenderer renderer=new PdfRenderer(pfd);if(renderer.getPageCount()<1){renderer.close();pfd.close();return null;}PdfRenderer.Page page=renderer.openPage(0);int width=Math.max(page.getWidth()*3,900);int height=Math.max(page.getHeight()*3,1200);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();renderer.close();pfd.close();return bitmap;}
    void fillFromReceipt(String raw){
        String vendor=findVendor(raw);
        if(!vendor.isEmpty())addVendor.setText(vendor);
        String amount=findAmount(raw);
        if(!amount.isEmpty())addAmount.setText(amount);
        String date=findDate(raw);
        if(!date.isEmpty())addDate.setText(date);else Toast.makeText(this,"Receipt date was not clear. Please confirm expense date.",Toast.LENGTH_LONG).show();
        String guessed=guessCategory(raw+" "+vendor);
        String[] allCats=getCategories();
        for(int i=0;i<allCats.length;i++){if(allCats[i].equals(guessed)){addCat.setSelection(i);break;}}
        if(addTitle.getText().toString().trim().isEmpty())addTitle.setText(vendor.isEmpty()?guessed+" receipt":vendor+" receipt");
        if(addDesc.getText().toString().trim().isEmpty())addDesc.setText("Business expense recorded from receipt for Virtual Beehive Inc.");
    }
    String findVendor(String raw){
        String[] lines=raw.split("\n");
        ArrayList<String> candidates=new ArrayList<>();
        for(int i=0;i<Math.min(lines.length,10);i++){
            String x=lines[i].trim().replaceAll("\\s+"," ");
            String low=x.toLowerCase(Locale.US);
            if(x.length()<3||x.length()>45)continue;
            if(x.matches(".*\\d{4,}.*"))continue;
            if(low.contains("receipt")||low.contains("invoice")||low.contains("date")||low.contains("time")||low.contains("cashier")||low.contains("register")||low.contains("terminal")||low.contains("visa")||low.contains("master")||low.contains("auth")||low.contains("approval")||low.contains("thank")||low.contains("total")||low.contains("subtotal")||low.contains("tax"))continue;
            candidates.add(x);
        }
        if(candidates.size()>0)return candidates.get(0);
        for(String l:lines){String x=l.trim();if(x.length()>2&&!x.matches(".*\\d{3,}.*"))return x;}
        return "";
    }
    ArrayList<String> amountsIn(String line){ArrayList<String> vals=new ArrayList<>();Matcher m=Pattern.compile("\\$?\\s*([0-9]{1,6}(?:,[0-9]{3})*\\.[0-9]{2})").matcher(line);while(m.find())vals.add(m.group(1).replace(",",""));return vals;}
    String findAmount(String raw){
        String[] lines=raw.split("\n");
        String best="";
        for(String line:lines){
            String low=line.toLowerCase(Locale.US).replace(":"," ");
            boolean totalLine=(low.matches(".*\\b(grand total|total due|amount due|balance due|total paid|sale total|total)\\b.*"));
            boolean bad=low.contains("subtotal")||low.contains("sub total")||low.contains("tax")||low.contains("change")||low.contains("cash back")||low.contains("tip")||low.contains("discount")||low.contains("saving")||low.contains("tender")||low.contains("paid by");
            if(totalLine&&!bad){ArrayList<String> vals=amountsIn(line);if(vals.size()>0)best=vals.get(vals.size()-1);}
        }
        if(!best.isEmpty())return best;
        double max=-1;String maxs="";
        for(String line:lines){String low=line.toLowerCase(Locale.US);if(low.contains("tax")||low.contains("subtotal")||low.contains("change")||low.contains("discount")||low.contains("visa")||low.contains("master")||low.contains("auth")||low.contains("approval"))continue;for(String v:amountsIn(line)){try{double d=Double.parseDouble(v);if(d>max&&d<100000){max=d;maxs=v;}}catch(Exception ignored){}}}
        return maxs;
    }
    String findDate(String raw){
        Matcher m=Pattern.compile("(20\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})").matcher(raw);if(m.find())return String.format(Locale.US,"%04d-%02d-%02d",Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
        m=Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})").matcher(raw);if(m.find()){int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2)),y=Integer.parseInt(m.group(3));if(y<100)y+=2000;return String.format(Locale.US,"%04d-%02d-%02d",y,a,b);}
        m=Pattern.compile("(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+(\\d{1,2}),?\\s+(20\\d{2})").matcher(raw);if(m.find()){String[] ms={"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};int mo=1;String g=m.group(1).toLowerCase(Locale.US);for(int i=0;i<ms.length;i++)if(g.startsWith(ms[i]))mo=i+1;return String.format(Locale.US,"%04d-%02d-%02d",Integer.parseInt(m.group(3)),mo,Integer.parseInt(m.group(2)));}
        return "";
    }
    String guessCategory(String raw){String x=raw.toLowerCase(Locale.US);if(x.contains("openai")||x.contains("chatgpt")||x.contains("midjourney")||x.contains("runway")||x.contains("elevenlabs")||x.contains("ai "))return "AI Tools Subscriptions";if(x.contains("google")||x.contains("workspace")||x.contains("subscription")||x.contains("software")||x.contains("hosting")||x.contains("domain"))return "Software & Subscriptions";if(x.contains("restaurant")||x.contains("cafe")||x.contains("coffee")||x.contains("lunch")||x.contains("dinner")||x.contains("pizza"))return "Business Meals";if(x.contains("uber")||x.contains("lyft")||x.contains("airline")||x.contains("hotel")||x.contains("parking")||x.contains("toll"))return "Travel";if(x.contains("fedex")||x.contains("ups")||x.contains("usps")||x.contains("shipping"))return "Postage & Shipping";if(x.contains("staples")||x.contains("office")||x.contains("paper")||x.contains("ink"))return "Office Supplies";if(x.contains("camera")||x.contains("computer")||x.contains("laptop")||x.contains("microphone")||x.contains("monitor"))return "Computer / Equipment";if(x.contains("ad")||x.contains("marketing")||x.contains("facebook")||x.contains("meta")||x.contains("tiktok"))return "Advertising & Marketing";return "Other Business Expense";}

    void saveExpense(Spinner cat,Spinner freq,EditText amount,EditText title,EditText desc,EditText vendor,EditText date){
        if(amount.getText().toString().trim().isEmpty()||title.getText().toString().trim().isEmpty()){Toast.makeText(this,"Please enter amount and title.",Toast.LENGTH_LONG).show();return;}
        String year=date.getText().toString().length()>=4?date.getText().toString().substring(0,4):new SimpleDateFormat("yyyy",Locale.US).format(new Date());
        String ref="VB-"+year+"-"+String.format(Locale.US,"%06d",getRecords().length()+1);
        JSONObject o=new JSONObject();
        try{
            o.put("ref",ref);o.put("entered",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));o.put("expenseDate",date.getText().toString());o.put("by",sp.getString("name","Daniel Pirooz"));o.put("position",sp.getString("position","CEO / Founder"));o.put("category",cat.getSelectedItem().toString());o.put("frequency",freq.getSelectedItem().toString());o.put("amount",amount.getText().toString());o.put("title",title.getText().toString());o.put("description",desc.getText().toString());o.put("vendor",vendor.getText().toString());
            String receiptPath=currentReceiptUri==null?"":copyReceiptToPrivate(currentReceiptUri,ref);o.put("receipt",receiptPath);o.put("syncStatus","Pending sync");o.put("syncMessage","Waiting for Google Sheets confirmation");
        }catch(Exception ignored){}
        JSONArray arr=getRecords();arr.put(o);sp.edit().putString("records",arr.toString()).apply();
        saveRecurringTemplateIfNeeded(o);
        syncRecord(o,year);
        new AlertDialog.Builder(this).setTitle("Expense recorded").setMessage("Reference Number:\n"+ref+"\n\nSaved locally and queued for Google Sheets."+(o.optString("frequency").equals("One-time")?"":"\n\nRecurring is active and will auto-create future rows until stopped in Records."))
            .setPositiveButton("Dashboard",(d,w)->showDashboard()).setNegativeButton("Add Another",(d,w)->showAdd()).show();
    }

    JSONArray getRecurringTemplates(){try{return new JSONArray(sp.getString("recurringTemplates","[]"));}catch(Exception e){return new JSONArray();}}
    void saveRecurringTemplates(JSONArray arr){sp.edit().putString("recurringTemplates",arr.toString()).apply();}
    void saveRecurringTemplateIfNeeded(JSONObject expense){
        try{
            String freq=expense.optString("frequency");
            if(freq.equals("One-time"))return;
            String id=expense.optString("title")+"|"+expense.optString("vendor")+"|"+expense.optString("amount")+"|"+freq;
            JSONArray arr=getRecurringTemplates();
            for(int i=0;i<arr.length();i++){JSONObject t=arr.getJSONObject(i);if(id.equals(t.optString("id"))){t.put("status","Active");t.put("nextDate",nextDate(expense.optString("expenseDate"),freq));saveRecurringTemplates(arr);return;}}
            JSONObject t=new JSONObject();
            t.put("id",id);t.put("status","Active");t.put("frequency",freq);t.put("nextDate",nextDate(expense.optString("expenseDate"),freq));
            t.put("category",expense.optString("category"));t.put("amount",expense.optString("amount"));t.put("title",expense.optString("title"));t.put("description",expense.optString("description"));t.put("vendor",expense.optString("vendor"));
            arr.put(t);saveRecurringTemplates(arr);
        }catch(Exception ignored){}
    }
    String nextDate(String date,String freq){
        try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);Calendar c=Calendar.getInstance();c.setTime(f.parse(date));if(freq.toLowerCase(Locale.US).contains("year"))c.add(Calendar.YEAR,1);else c.add(Calendar.MONTH,1);return f.format(c.getTime());}catch(Exception e){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    }
    boolean dateDue(String date){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);Date d=f.parse(date);Date today=f.parse(f.format(new Date()));return !d.after(today);}catch(Exception e){return false;}}
    boolean recordExistsForRecurring(String key){JSONArray arr=getRecords();for(int i=0;i<arr.length();i++){try{if(key.equals(arr.getJSONObject(i).optString("recurringKey")))return true;}catch(Exception ignored){}}return false;}
    void generateDueRecurringExpenses(){
        try{
            JSONArray templates=getRecurringTemplates();JSONArray records=getRecords();boolean changed=false;
            ArrayList<JSONObject> toSync=new ArrayList<>();
            for(int i=0;i<templates.length();i++){
                JSONObject t=templates.getJSONObject(i);if(!"Active".equalsIgnoreCase(t.optString("status")))continue;
                int guard=0;
                while(dateDue(t.optString("nextDate")) && guard<24){guard++;
                    String due=t.optString("nextDate");String key=t.optString("id")+"|"+due;
                    if(!recordExistsForRecurring(key)){
                        String year=due.length()>=4?due.substring(0,4):new SimpleDateFormat("yyyy",Locale.US).format(new Date());
                        String ref="VB-"+year+"-"+String.format(Locale.US,"%06d",records.length()+1);
                        JSONObject o=new JSONObject();
                        o.put("ref",ref);o.put("entered",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));o.put("expenseDate",due);o.put("by",sp.getString("name","Daniel Pirooz"));o.put("position",sp.getString("position","CEO / Founder"));
                        o.put("category",t.optString("category"));o.put("frequency",t.optString("frequency"));o.put("amount",t.optString("amount"));o.put("title",t.optString("title"));o.put("description",t.optString("description")+" (Auto-recurring expense)");o.put("vendor",t.optString("vendor"));o.put("receipt","");o.put("syncStatus","Pending sync");o.put("syncMessage","Auto-created recurring expense");o.put("recurringKey",key);
                        records.put(o);toSync.add(o);changed=true;
                    }
                    t.put("nextDate",nextDate(due,t.optString("frequency")));
                    changed=true;
                }
            }
            if(changed){sp.edit().putString("records",records.toString()).putString("recurringTemplates",templates.toString()).apply();for(JSONObject o:toSync)syncRecord(o,expenseYear(o));}
        }catch(Exception ignored){}
    }
    void stopRecurring(String id){try{JSONArray arr=getRecurringTemplates();for(int i=0;i<arr.length();i++){JSONObject t=arr.getJSONObject(i);if(id.equals(t.optString("id")))t.put("status","Stopped");}saveRecurringTemplates(arr);Toast.makeText(this,"Recurring expense stopped",Toast.LENGTH_LONG).show();showRecords();}catch(Exception e){Toast.makeText(this,"Could not stop recurring: "+e.getMessage(),Toast.LENGTH_LONG).show();}}

    int countPendingSync(){int c=0;JSONArray arr=getRecords();for(int i=0;i<arr.length();i++){try{JSONObject o=arr.getJSONObject(i);if(!"Synced".equalsIgnoreCase(o.optString("syncStatus")))c++;}catch(Exception ignored){}}return c;}
    String expenseYear(JSONObject o){String d=o.optString("expenseDate");if(d!=null&&d.length()>=4&&d.substring(0,4).matches("\\d{4}"))return d.substring(0,4);return new SimpleDateFormat("yyyy",Locale.US).format(new Date());}
    void retryPendingSync(){generateDueRecurringExpenses();GoogleSignInAccount acct=GoogleSignIn.getLastSignedInAccount(this);if(acct==null){Toast.makeText(this,"Please connect Google first.",Toast.LENGTH_LONG).show();return;}JSONArray arr=getRecords();int started=0;for(int i=0;i<arr.length();i++){try{JSONObject o=arr.getJSONObject(i);if(!"Synced".equalsIgnoreCase(o.optString("syncStatus"))){started++;syncRecord(o,expenseYear(o));}}catch(Exception ignored){}}Toast.makeText(this,started==0?"No pending rows to sync.":"Retrying "+started+" pending row(s).",Toast.LENGTH_LONG).show();}
    void markRecordSync(String ref,boolean ok,String msg){try{JSONArray arr=getRecords();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);if(ref.equals(o.optString("ref"))){o.put("syncStatus",ok?"Synced":"Pending sync");o.put("syncMessage",msg);if(ok)o.put("syncedAt",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));break;}}sp.edit().putString("records",arr.toString()).putString("lastSyncError",ok?"":msg).apply();}catch(Exception ignored){}}
    void syncRecord(JSONObject o,String year){GoogleSignInAccount acct=GoogleSignIn.getLastSignedInAccount(this);String ref=o.optString("ref");if(acct==null){markRecordSync(ref,false,"Connect Google Account, then tap Retry Pending Sync.");Toast.makeText(this,"Saved locally. Connect Google to sync.",Toast.LENGTH_LONG).show();return;}new Thread(()->{try{performSyncRecord(acct,o,year);markRecordSync(ref,true,"Synced to VB expenses "+year);runOnUiThread(()->Toast.makeText(this,"Synced to VB expenses "+year,Toast.LENGTH_LONG).show());}catch(UserRecoverableAuthException e){markRecordSync(ref,false,"Google permission needed. Please approve and retry.");runOnUiThread(()->startActivityForResult(e.getIntent(),901));}catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();markRecordSync(ref,false,m);runOnUiThread(()->Toast.makeText(this,"Google sync failed. Open Dashboard > Retry Pending Sync. "+m,Toast.LENGTH_LONG).show());}}).start();}
    void performSyncRecord(GoogleSignInAccount acct,JSONObject o,String year)throws Exception{Exception last=null;for(int attempt=0;attempt<2;attempt++){String token=null;try{token=GoogleAuthUtil.getToken(this,acct.getAccount(),"oauth2:"+SCOPE_DRIVE+" "+SCOPE_SHEETS);String sheetId=getOrCreateSheet(token,year);appendRow(token,sheetId,o);return;}catch(UserRecoverableAuthException e){throw e;}catch(Exception e){last=e;String m=e.getMessage()==null?"":e.getMessage();if(token!=null&&(m.contains("HTTP 401")||m.contains("HTTP 403"))&&attempt==0){try{GoogleAuthUtil.clearToken(this,token);}catch(Exception ignored){}continue;}throw e;}}if(last!=null)throw last;}
    String getOrCreateSheet(String token,String year)throws Exception{String key="sheet_"+year;String expected="VB expenses "+year;String id=sp.getString(key,"");if(!id.isEmpty()){try{JSONObject check=new JSONObject(http("GET","https://www.googleapis.com/drive/v3/files/"+id+"?fields=id,name,trashed",token,null));if(!check.optBoolean("trashed",false)&&expected.equals(check.optString("name"))){ensureSheetFormatted(token,id,year,false);return id;}}catch(Exception e){sp.edit().remove(key).apply();}}
        String q="'"+DRIVE_FOLDER_ID+"' in parents and name = '"+expected+"' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false";
        String search=http("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id,name,createdTime)&orderBy=createdTime&pageSize=10",token,null);JSONArray files=new JSONObject(search).optJSONArray("files");if(files!=null&&files.length()>0){id=files.getJSONObject(0).getString("id");SharedPreferences.Editor ed=sp.edit().putString(key,id);if(files.length()>1)ed.putString("lastSyncError","Multiple "+expected+" sheets were found. Using the oldest one; please archive/delete duplicates manually if needed.");ed.apply();ensureSheetFormatted(token,id,year,false);return id;}
        JSONObject body=new JSONObject();body.put("name",expected);body.put("mimeType","application/vnd.google-apps.spreadsheet");body.put("parents",new JSONArray().put(DRIVE_FOLDER_ID));body.put("appProperties",new JSONObject().put("vbExpenseYear",year).put("vbExpenseSheet","true"));JSONObject res=new JSONObject(http("POST","https://www.googleapis.com/drive/v3/files?fields=id,name",token,body.toString()));id=res.getString("id");sp.edit().putString(key,id).apply();ensureSheetFormatted(token,id,year,true);return id;}
    JSONArray headerRow(){return new JSONArray(Arrays.asList("Reference Number","Date Entered","Entered By","Position","Expense Date","Year","Expense Category","Frequency","Title","Description / Business Purpose","Vendor / Store","Amount","Receipt Link","Company","Status"));}
    void ensureSheetFormatted(String token,String sheetId,String year,boolean created)throws Exception{boolean already=false;try{String res=http("GET","https://sheets.googleapis.com/v4/spreadsheets/"+sheetId+"/values/Sheet1!B1:B1",token,null);JSONObject o=new JSONObject(res);JSONArray vals=o.optJSONArray("values");already=vals!=null&&vals.length()>0&&vals.getJSONArray(0).optString(0).contains("Virtual Beehive Inc.");}catch(Exception ignored){}if(!already){if(!created){JSONObject insert=new JSONObject().put("requests",new JSONArray().put(new JSONObject().put("insertDimension",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("dimension","ROWS").put("startIndex",0).put("endIndex",3)).put("inheritFromBefore",false))));try{http("POST","https://sheets.googleapis.com/v4/spreadsheets/"+sheetId+":batchUpdate",token,insert.toString());}catch(Exception ignored){}}
            JSONArray values=new JSONArray();values.put(new JSONArray(Arrays.asList("=IMAGE(\"https://drive.google.com/uc?export=view&id="+getOrUploadLogo(token)+"\",1)","Virtual Beehive Inc. Expense Bookkeeping - "+year,"","","","","","","","","","","","","")));values.put(new JSONArray(Arrays.asList("","Private company expense log. One Google Sheet per year. Records are append-only; corrections should be added as notes/new rows.","","","","","","","","","","","","","")));values.put(headerRow());JSONObject body=new JSONObject().put("values",values);http("PUT","https://sheets.googleapis.com/v4/spreadsheets/"+sheetId+"/values/Sheet1!A1:O3?valueInputOption=USER_ENTERED",token,body.toString());}
        formatSheet(token,sheetId);}
    String getOrUploadLogo(String token)throws Exception{String saved=sp.getString("driveLogoId","");if(!saved.isEmpty())return saved;ByteArrayOutputStream baos=new ByteArrayOutputStream();BitmapFactory.decodeResource(getResources(),getResources().getIdentifier("vb_logo","drawable",getPackageName())).compress(Bitmap.CompressFormat.PNG,100,baos);String boundary="vbBoundary"+System.currentTimeMillis();JSONObject meta=new JSONObject();meta.put("name","Virtual Beehive Inc logo.png");meta.put("parents",new JSONArray().put(DRIVE_FOLDER_ID));String start="--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta.toString()+"\r\n--"+boundary+"\r\nContent-Type: image/png\r\n\r\n";String end="\r\n--"+boundary+"--";HttpURLConnection c=(HttpURLConnection)new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart").openConnection();c.setRequestMethod("POST");c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","multipart/related; boundary="+boundary);c.setDoOutput(true);OutputStream os=c.getOutputStream();os.write(start.getBytes("UTF-8"));os.write(baos.toByteArray());os.write(end.getBytes("UTF-8"));os.close();int code=c.getResponseCode();String res=readAll(code>=200&&code<300?c.getInputStream():c.getErrorStream());if(code<200||code>=300)throw new Exception("Logo upload HTTP "+code);String id=new JSONObject(res).getString("id");sp.edit().putString("driveLogoId",id).apply();return id;}
    void formatSheet(String token,String sheetId)throws Exception{JSONArray req=new JSONArray();req.put(new JSONObject().put("updateSheetProperties",new JSONObject().put("properties",new JSONObject().put("sheetId",0).put("gridProperties",new JSONObject().put("frozenRowCount",3))).put("fields","gridProperties.frozenRowCount")));req.put(new JSONObject().put("setBasicFilter",new JSONObject().put("filter",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("startRowIndex",2).put("startColumnIndex",0).put("endColumnIndex",15)))));req.put(new JSONObject().put("repeatCell",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("startRowIndex",0).put("endRowIndex",2).put("startColumnIndex",0).put("endColumnIndex",15)).put("cell",new JSONObject().put("userEnteredFormat",new JSONObject().put("backgroundColor",color(0.05,0.04,0.02)).put("textFormat",new JSONObject().put("foregroundColor",color(1,0.74,0.18)).put("fontSize",18).put("bold",true)))).put("fields","userEnteredFormat(backgroundColor,textFormat)")));req.put(new JSONObject().put("repeatCell",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("startRowIndex",2).put("endRowIndex",3).put("startColumnIndex",0).put("endColumnIndex",15)).put("cell",new JSONObject().put("userEnteredFormat",new JSONObject().put("backgroundColor",color(0.96,0.57,0.08)).put("horizontalAlignment","CENTER").put("textFormat",new JSONObject().put("foregroundColor",color(0.03,0.025,0.015)).put("fontSize",11).put("bold",true)))).put("fields","userEnteredFormat(backgroundColor,horizontalAlignment,textFormat)")));req.put(new JSONObject().put("updateDimensionProperties",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("dimension","ROWS").put("startIndex",0).put("endIndex",1)).put("properties",new JSONObject().put("pixelSize",64)).put("fields","pixelSize")));req.put(new JSONObject().put("updateDimensionProperties",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("dimension","ROWS").put("startIndex",2).put("endIndex",3)).put("properties",new JSONObject().put("pixelSize",36)).put("fields","pixelSize")));req.put(new JSONObject().put("autoResizeDimensions",new JSONObject().put("dimensions",new JSONObject().put("sheetId",0).put("dimension","COLUMNS").put("startIndex",0).put("endIndex",15))));req.put(new JSONObject().put("addBanding",new JSONObject().put("bandedRange",new JSONObject().put("range",new JSONObject().put("sheetId",0).put("startRowIndex",3).put("startColumnIndex",0).put("endColumnIndex",15)).put("rowProperties",new JSONObject().put("firstBandColor",color(1,0.97,0.90)).put("secondBandColor",color(1,0.91,0.72))))));JSONObject body=new JSONObject().put("requests",req);try{http("POST","https://sheets.googleapis.com/v4/spreadsheets/"+sheetId+":batchUpdate",token,body.toString());}catch(Exception e){/* formatting only; do not block expense save */}}
    JSONObject color(double r,double g,double b)throws Exception{return new JSONObject().put("red",r).put("green",g).put("blue",b);}
    void appendRow(String token,String sheetId,JSONObject o)throws Exception{String year=o.optString("expenseDate").length()>=4?o.optString("expenseDate").substring(0,4):"";String receiptLink="";try{receiptLink=uploadReceiptFile(token,o,year);}catch(Exception e){receiptLink=o.optString("receipt");}JSONArray row=new JSONArray().put(o.optString("ref")).put(o.optString("entered")).put(o.optString("by")).put(o.optString("position")).put(o.optString("expenseDate")).put(year).put(o.optString("category")).put(o.optString("frequency")).put(o.optString("title")).put(o.optString("description")).put(o.optString("vendor")).put(o.optString("amount")).put(receiptLink).put("Virtual Beehive Inc.").put("New");JSONObject body=new JSONObject().put("values",new JSONArray().put(row));http("POST","https://sheets.googleapis.com/v4/spreadsheets/"+sheetId+"/values/Sheet1!A:O:append?valueInputOption=USER_ENTERED",token,body.toString());}
    String getOrCreateReceiptFolder(String token,String year)throws Exception{String key="receiptFolder_"+year;String id=sp.getString(key,"");if(!id.isEmpty())return id;String name="Receipts "+year;String q="'"+DRIVE_FOLDER_ID+"' in parents and name = '"+name+"' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";String search=http("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id,name)&pageSize=1",token,null);JSONArray files=new JSONObject(search).optJSONArray("files");if(files!=null&&files.length()>0){id=files.getJSONObject(0).getString("id");sp.edit().putString(key,id).apply();return id;}JSONObject meta=new JSONObject().put("name",name).put("mimeType","application/vnd.google-apps.folder").put("parents",new JSONArray().put(DRIVE_FOLDER_ID));JSONObject res=new JSONObject(http("POST","https://www.googleapis.com/drive/v3/files?fields=id",token,meta.toString()));id=res.getString("id");sp.edit().putString(key,id).apply();return id;}
    String uploadReceiptFile(String token,JSONObject o,String year)throws Exception{String rec=o.optString("receipt","");if(rec.isEmpty())return "";File f=new File(rec);InputStream in;if(f.exists())in=new FileInputStream(f);else in=getContentResolver().openInputStream(Uri.parse(rec));if(in==null)return rec;ByteArrayOutputStream baos=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)baos.write(buf,0,n);in.close();String folder=getOrCreateReceiptFolder(token,year);String boundary="receiptBoundary"+System.currentTimeMillis();String contentType=f.exists()?mimeForFile(f):receiptMime(Uri.parse(rec));if(contentType==null||contentType.isEmpty())contentType="application/octet-stream";String ext=".jpg";if(contentType.contains("pdf"))ext=".pdf";else if(contentType.contains("png"))ext=".png";else if(contentType.contains("webp"))ext=".webp";String fileName=o.optString("ref","receipt")+ext;JSONObject meta=new JSONObject().put("name",fileName).put("parents",new JSONArray().put(folder));String start="--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta.toString()+"\r\n--"+boundary+"\r\nContent-Type: "+contentType+"\r\n\r\n";String end="\r\n--"+boundary+"--";HttpURLConnection c=(HttpURLConnection)new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink").openConnection();c.setRequestMethod("POST");c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","multipart/related; boundary="+boundary);c.setDoOutput(true);OutputStream os=c.getOutputStream();os.write(start.getBytes("UTF-8"));os.write(baos.toByteArray());os.write(end.getBytes("UTF-8"));os.close();int code=c.getResponseCode();String res=readAll(code>=200&&code<300?c.getInputStream():c.getErrorStream());if(code<200||code>=300)throw new Exception("Receipt upload HTTP "+code+" "+res);JSONObject jo=new JSONObject(res);return jo.optString("webViewLink","https://drive.google.com/file/d/"+jo.getString("id")+"/view");}
    String http(String method,String url,String token,String json)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setDoInput(true);if(json!=null){c.setDoOutput(true);OutputStream os=c.getOutputStream();os.write(json.getBytes("UTF-8"));os.close();}int code=c.getResponseCode();InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();String s=readAll(is);if(code<200||code>=300)throw new Exception("HTTP "+code+" "+s);return s;}
    String readAll(InputStream is)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(is));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);return sb.toString();}

    void showRecurringControls(){
        JSONArray arr=getRecurringTemplates();
        if(arr.length()==0)return;
        LinearLayout rc=card("Recurring Expenses");
        rc.addView(tv("Monthly/yearly items auto-create new expense rows on the same date and amount. Tap Stop to cancel future repeats.",13,MUTED,Typeface.NORMAL));
        for(int i=0;i<arr.length();i++){
            try{
                JSONObject t=arr.getJSONObject(i);
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,dp(8),0,dp(8));
                row.addView(tv(t.optString("title")+" • $"+t.optString("amount"),15,WHITE,Typeface.BOLD));
                row.addView(tv(t.optString("frequency")+" • Next: "+t.optString("nextDate")+" • "+t.optString("status"),13,MUTED,Typeface.NORMAL));
                if("Active".equalsIgnoreCase(t.optString("status"))){TextView stop=secondaryButton("Stop this recurring expense");final String id=t.optString("id");stop.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Stop recurring expense?").setMessage("This stops future automatic rows for this recurring charge. Existing recorded expenses will remain locked.").setPositiveButton("Stop",(d,w)->stopRecurring(id)).setNegativeButton("Cancel",null).show());add(row,stop,-1,-2,0,8,0,0);} 
                add(rc,row,-1,-2,0,4,0,4);
            }catch(Exception ignored){}
        }
    }

    public void showRecords(){base("Records");
        LinearLayout cloud=card("Google Sync Controls");String email=sp.getString("googleEmail","");int pending=countPendingSync();cloud.addView(tv(email.isEmpty()?"Not connected":"Connected as: "+email,15,email.isEmpty()?HONEY_LIGHT:WHITE,Typeface.BOLD));cloud.addView(tv(pending==0?"No pending local expenses.":pending+" expense(s) pending Google Sheet sync.",14,pending==0?MUTED:HONEY_LIGHT,Typeface.BOLD));String lastErr=sp.getString("lastSyncError","");if(!lastErr.isEmpty())cloud.addView(tv("Last sync message: "+lastErr,12,HONEY_LIGHT,Typeface.NORMAL));TextView connect=secondaryButton(email.isEmpty()?"Connect Google Account":"Reconnect Google Account");connect.setOnClickListener(v->startActivityForResult(signInClient.getSignInIntent(),900));add(cloud,connect,-1,-2,0,12,0,8);TextView retry=primaryButton("Retry Pending Sync");retry.setOnClickListener(v->retryPendingSync());add(cloud,retry,-1,-2,0,0,0,8);TextView reset=secondaryButton("Reset Sheet Link and Retry");reset.setOnClickListener(v->{clearAllSheetLinks();Toast.makeText(this,"Sheet links cleared. Tap Retry Pending Sync.",Toast.LENGTH_LONG).show();showRecords();});add(cloud,reset,-1,-2,0,0,0,0);
        showRecurringControls();
        JSONArray arr=getRecords();if(arr.length()==0){LinearLayout e=card("No Records Yet");e.addView(tv("Add an expense and it will appear here with a locked reference number.",15,WHITE,Typeface.NORMAL));return;}for(int i=arr.length()-1;i>=0;i--){try{JSONObject o=arr.getJSONObject(i);LinearLayout c=card(o.optString("ref"));TextView main=tv("$"+o.optString("amount")+"  •  "+o.optString("title"),18,WHITE,Typeface.BOLD);main.setPadding(0,dp(8),0,dp(6));c.addView(main);c.addView(tv(o.optString("category")+"\n"+o.optString("frequency")+"\n"+o.optString("entered")+"\nEntered by: "+o.optString("by")+"\nSync: "+o.optString("syncStatus","Pending sync"),14,MUTED,Typeface.NORMAL));}catch(Exception ignored){}}
    }
    public void showProfile(){
        try{
            base("Profile");
            LinearLayout c=card("User Profile");
            String photo=sp.getString("profilePhoto","");
            if(!photo.isEmpty()){
                profileImageView=new ImageView(this);
                profileImageView.setBackground(outlined(Color.rgb(22,18,14),24,HONEY));
                profileImageView.setScaleType(ImageView.ScaleType.MATRIX);
                loadProfileBitmap(photo);
                add(c,profileImageView,-1,dp(190),0,10,0,8);
                LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);
                String[] toolNames={"Zoom +","Zoom -","Up","Down","Reset"};
                for(String tool:toolNames){TextView b=secondaryButton(tool);b.setTextSize(11);b.setOnClickListener(v->adjustProfileImage(((TextView)v).getText().toString()));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(2),0,dp(2),0);tools.addView(b,lp);} 
                add(c,tools,-1,-2,0,0,0,10);
            }
            TextView avatar=secondaryButton(photo.isEmpty()?"+ Add Profile Picture":"Change Profile Picture");
            avatar.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(Intent.createChooser(i,"Choose profile picture"),55);});
            add(c,avatar,-1,-2,0,photo.isEmpty()?10:0,0,12);

            TextView namePreview=tv(sp.getString("name","Daniel Pirooz"),22,WHITE,Typeface.BOLD);namePreview.setGravity(Gravity.CENTER);add(c,namePreview,-1,-2,0,2,0,0);
            TextView posPreview=tv(sp.getString("position","CEO / Founder"),17,HONEY_LIGHT,Typeface.BOLD);posPreview.setGravity(Gravity.CENTER);add(c,posPreview,-1,-2,0,2,0,12);

            label(c,"Full Name");EditText name=input("Full name");name.setText(sp.getString("name","Daniel Pirooz"));add(c,name,-1,-2,0,0,0,2);
            label(c,"Position");EditText pos=input("Position");pos.setText(sp.getString("position","CEO / Founder"));add(c,pos,-1,-2,0,0,0,2);
            label(c,"Phone");EditText phone=input("Phone number");phone.setText(sp.getString("phone",""));add(c,phone,-1,-2,0,0,0,2);
            label(c,"Email");EditText email=input("Email");email.setText(sp.getString("email","danny@virtualbeehiveinc.com"));add(c,email,-1,-2,0,0,0,12);

            TextView save=primaryButton("Save Profile + Update QR");
            save.setOnClickListener(v->{sp.edit().putString("name",name.getText().toString()).putString("position",pos.getText().toString()).putString("phone",phone.getText().toString()).putString("email",email.getText().toString()).apply();Toast.makeText(this,"Profile saved",Toast.LENGTH_LONG).show();showProfile();});
            add(c,save,-1,-2,0,0,0,12);

            LinearLayout qrCard=card("Contact QR Code");
            qrCard.addView(tv("Another phone can scan this QR code to save your contact card.",14,MUTED,Typeface.NORMAL));
            Bitmap qr=makeQr(vcard(name.getText().toString(),pos.getText().toString(),phone.getText().toString(),email.getText().toString()),dp(210));
            if(qr!=null){ImageView qv=new ImageView(this);qv.setImageBitmap(qr);qv.setBackgroundColor(Color.WHITE);qv.setPadding(dp(10),dp(10),dp(10),dp(10));add(qrCard,qv,dp(230),dp(230),0,12,0,0);}else qrCard.addView(tv("QR code could not be generated on this device.",14,HONEY_LIGHT,Typeface.BOLD));
        }catch(Exception e){
            Toast.makeText(this,"Profile error fixed-safe mode: "+e.getMessage(),Toast.LENGTH_LONG).show();
            base("Profile");LinearLayout c=card("Profile Safe Mode");c.addView(tv("Profile opened safely. Please change the profile picture if the old image caused the crash.",15,WHITE,Typeface.NORMAL));
            TextView change=secondaryButton("Choose New Profile Picture");change.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(Intent.createChooser(i,"Choose profile picture"),55);});add(c,change,-1,-2,0,12,0,0);
        }
    }

    void loadProfileBitmap(String path){try{profileBitmap=BitmapFactory.decodeFile(path);if(profileBitmap!=null)applyProfileMatrix();}catch(Exception ignored){}}
    void adjustProfileImage(String action){float z=sp.getFloat("profileZoom",1.0f);float y=sp.getFloat("profileOffsetY",0f);if(action.equals("Zoom +"))z+=0.1f;else if(action.equals("Zoom -"))z=Math.max(0.5f,z-0.1f);else if(action.equals("Up"))y-=20f;else if(action.equals("Down"))y+=20f;else {z=1.0f;y=0f;}sp.edit().putFloat("profileZoom",z).putFloat("profileOffsetY",y).apply();applyProfileMatrix();}
    void applyProfileMatrix(){if(profileImageView==null||profileBitmap==null)return;profileImageView.setImageBitmap(profileBitmap);profileImageView.post(()->{try{float vw=profileImageView.getWidth(),vh=profileImageView.getHeight();float bw=profileBitmap.getWidth(),bh=profileBitmap.getHeight();float base=Math.max(vw/bw,vh/bh);float zoom=sp.getFloat("profileZoom",1.0f);float scale=base*zoom;float dx=(vw-bw*scale)/2f;float dy=(vh-bh*scale)/2f+sp.getFloat("profileOffsetY",0f);Matrix m=new Matrix();m.setScale(scale,scale);m.postTranslate(dx,dy);profileImageView.setImageMatrix(m);}catch(Exception ignored){}});}
    String vcard(String name,String pos,String phone,String email){return "BEGIN:VCARD\nVERSION:3.0\nFN:"+name+"\nORG:Virtual Beehive Inc.\nTITLE:"+pos+"\nTEL:"+phone+"\nEMAIL:"+email+"\nEND:VCARD";}
    Bitmap makeQr(String data,int size){try{BitMatrix bm=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap img=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int x=0;x<size;x++)for(int y=0;y<size;y++)img.setPixel(x,y,bm.get(x,y)?Color.BLACK:Color.WHITE);return img;}catch(Exception e){return null;}}

}
