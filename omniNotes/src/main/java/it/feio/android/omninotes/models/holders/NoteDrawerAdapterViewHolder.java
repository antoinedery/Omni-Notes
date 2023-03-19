package it.feio.android.omninotes.models.holders;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import com.neopixl.pixlui.components.textview.TextView;

import java.util.List;

import it.feio.android.omninotes.models.NavigationItem;

import it.feio.android.omninotes.R;

/**
 * Holder object
 *
 * @author fede
 */
public class NoteDrawerAdapterViewHolder {

    ImageView imgIcon;
    TextView txtTitle;

    public View findViewById(int param, View convertView) {
        return convertView.findViewById(param);
    }

    public void setImgIcon(View convertView) {
        this.imgIcon = (ImageView) this.findViewById(R.id.icon, convertView);
    }

    public void setTxtTitle(View convertView) {
        this.txtTitle = (TextView) this.findViewById(R.id.title, convertView);
    }

    public void setResultsToTextViews(String text) {
        this.txtTitle.setText(text);
    }

    public void setImageResource(int imageResource) {
        this.imgIcon.setImageResource(imageResource);
    }

    public void setTypeface(int style) {
        this.txtTitle.setTypeface(null, style);
    }

    public void setTextColor(int color) {
        this.txtTitle.setTextColor(color);
    }

    public void setColorFilter(int color) {
        this.imgIcon.getDrawable().mutate().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    public void setSelectedHolderAttributes(List<NavigationItem> items, int position, Activity mActivity) {
        this.setImageResource(items.get(position).getIconSelected());
        this.setTypeface(Typeface.BOLD);
        int color = this.getColor(R.color.colorPrimaryDark, mActivity);
        this.setTextColor(color);
        this.setColorFilter(color);
    }

    public void setUnselectedHolderAttributes(List<NavigationItem> items, int position, Activity mActivity) {
        this.setImageResource(items.get(position).getIcon());
        this.setTypeface(Typeface.NORMAL);
        int color = this.getColor(R.color.drawer_text, mActivity);
        this.setTextColor(color);
    }

    public void setInitialHolderAttributes(View convertView) {
        this.setImgIcon(convertView);
        this.setTxtTitle(convertView);
    }

    public void setHolderAttributes(List<NavigationItem> items, int position, Activity mActivity, boolean isSelected) {
        // Set the results into TextViews
        this.setResultsToTextViews(items.get(position).getText());

        if (isSelected) {
            this.setSelectedHolderAttributes(items, position, mActivity);
        } else {
            this.setUnselectedHolderAttributes(items, position, mActivity);
        }
    }
    public int getColor(int color, Activity mActivity) {
        return mActivity.getResources().getColor(color);
    }
}
