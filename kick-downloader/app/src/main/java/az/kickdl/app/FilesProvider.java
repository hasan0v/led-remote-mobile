package az.kickdl.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Serves files from the app's own storage to other apps (share / external player). */
public class FilesProvider extends ContentProvider {
    public static final String AUTHORITY = "az.kickdl.app.files";

    private static File root(Context c) {
        File r = c.getExternalFilesDir(null);
        return r != null ? r : c.getFilesDir();
    }

    public static Uri uriFor(Context c, File f) {
        String rootPath = root(c).getAbsolutePath();
        String p = f.getAbsolutePath();
        String rel = p.startsWith(rootPath) ? p.substring(rootPath.length()) : p;
        if (rel.startsWith("/")) rel = rel.substring(1);
        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTHORITY);
        for (String part : rel.split("/")) b.appendPath(part);
        return b.build();
    }

    public static File fileFor(Context c, Uri uri) {
        try {
            File rootDir = root(c).getCanonicalFile();
            File f = new File(rootDir, uri.getPath()).getCanonicalFile();
            if (!f.getPath().startsWith(rootDir.getPath())) return null;
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = fileFor(getContext(), uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        if ("ts".equals(ext)) return "video/mp2t";
        if ("m4a".equals(ext)) return "audio/mp4";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : "application/octet-stream";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(getContext(), uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
