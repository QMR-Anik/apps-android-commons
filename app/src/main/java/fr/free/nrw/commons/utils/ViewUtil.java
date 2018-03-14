package fr.free.nrw.commons.utils;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.Toast;

public class ViewUtil {

    public static void showLongToast(final Context context, @StringRes final int stringResId) {
        ExecutorUtils.uiExecutor().execute(() -> Toast.makeText(context, context.getString(stringResId), Toast.LENGTH_LONG).show());
    }

    public static void showLongSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    public static void showSnackbar(View view, int messageResourceId) {
        Snackbar.make(view, messageResourceId, Snackbar.LENGTH_SHORT).show();
    }

}
