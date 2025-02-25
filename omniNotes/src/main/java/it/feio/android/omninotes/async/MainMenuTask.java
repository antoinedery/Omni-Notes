/*
 * Copyright (C) 2013-2022 Federico Iosue (federico@iosue.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.feio.android.omninotes.async;

import static it.feio.android.omninotes.utils.ConstantsBase.PREF_DYNAMIC_MENU;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_SHOW_UNCATEGORIZED;

import android.content.Intent;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Build;

import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.pixplicity.easyprefs.library.Prefs;
import de.greenrobot.event.EventBus;
import it.feio.android.omninotes.MainActivity;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.async.bus.NavigationUpdatedEvent;
import it.feio.android.omninotes.models.NavigationItem;
import it.feio.android.omninotes.models.adapters.NavDrawerAdapter;
import it.feio.android.omninotes.models.misc.DynamicNavigationLookupTable;
import it.feio.android.omninotes.models.views.NonScrollableListView;
import it.feio.android.omninotes.utils.Navigation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
public class MainMenuTask extends AsyncTask<Void, Void, List<NavigationItem>> {

  private final WeakReference<Fragment> mFragmentWeakReference;
  private final MainActivity mainActivity;
  @BindView(R.id.drawer_nav_list)
  NonScrollableListView mDrawerList;
  @BindView(R.id.drawer_tag_list)
  NonScrollableListView mDrawerCategoriesList;


  public MainMenuTask(Fragment mFragment) {
    mFragmentWeakReference = new WeakReference<>(mFragment);
    this.mainActivity = (MainActivity) mFragment.getActivity();
    ButterKnife.bind(this, mFragment.getView());
  }

  @Override
  protected List<NavigationItem> doInBackground(Void... params) {
    return buildMainMenu();
  }

  @Override
  protected void onPostExecute(final List<NavigationItem> items) {
    if (isAlive()) {
      mDrawerList.setAdapter(new NavDrawerAdapter(mainActivity, items));
      mDrawerList.setOnItemClickListener((arg0, arg1, position, arg3) -> {
        String navigation = mFragmentWeakReference.get().getResources().getStringArray(R.array
            .navigation_list_codes)[items.get(position).getArrayIndex()];
        updateNavigation(position, navigation);
      });
      mDrawerList.justifyListViewHeightBasedOnChildren();
    }
  }

  private void updateNavigation(int position, String navigation) {
    if (mainActivity.updateNavigation(navigation)) {
      mDrawerList.setItemChecked(position, true);
      if (mDrawerCategoriesList != null) {
        mDrawerCategoriesList.setItemChecked(0, false); // Called to force redraw
      }
      mainActivity.getIntent().setAction(Intent.ACTION_MAIN);
      EventBus.getDefault()
          .post(new NavigationUpdatedEvent(mDrawerList.getItemAtPosition(position)));
    }
  }

  private boolean isAlive() {
    return mFragmentWeakReference.get() != null
        && mFragmentWeakReference.get().isAdded()
        && mFragmentWeakReference.get().getActivity() != null
        && !mFragmentWeakReference.get().getActivity().isFinishing();
  }

  private List<NavigationItem> buildMainMenu() {
    if (!isAlive()) {
      return new ArrayList<>();
    }

    String[] mNavigationArray = mainActivity.getResources().getStringArray(R.array.navigation_list);
    TypedArray mNavigationIconsArray = mainActivity.getResources()
        .obtainTypedArray(R.array.navigation_list_icons);
    TypedArray mNavigationIconsSelectedArray = mainActivity.getResources().obtainTypedArray(R.array
        .navigation_list_icons_selected);

    final List<NavigationItem> items = new ArrayList<>();
    for (int i = 0; i < mNavigationArray.length; i++) {
      if (!checkSkippableItem(i)) {
        NavigationItem item = new NavigationItem(i, mNavigationArray[i],
            mNavigationIconsArray.getResourceId(i,
                0), mNavigationIconsSelectedArray.getResourceId(i, 0));
        items.add(item);
      }
    }
    return items;
  }

//  private boolean checkSkippableItem(int i) {
//    boolean skippable = false;
//    boolean dynamicMenu = Prefs.getBoolean(PREF_DYNAMIC_MENU, true);
//    DynamicNavigationLookupTable dynamicNavigationLookupTable = null;
//    if (dynamicMenu) {
//      dynamicNavigationLookupTable = DynamicNavigationLookupTable.getInstance();
//    }
//    switch (i) {
//      case Navigation.REMINDERS:
//        if (dynamicMenu && dynamicNavigationLookupTable.getReminders() == 0) {
//          skippable = true;
//        }
//        break;
//      case Navigation.UNCATEGORIZED:
//        boolean showUncategorized = Prefs.getBoolean(PREF_SHOW_UNCATEGORIZED, false);
//        if (!showUncategorized || (dynamicMenu
//            && dynamicNavigationLookupTable.getUncategorized() == 0)) {
//          skippable = true;
//        }
//        break;
//      case Navigation.ARCHIVE:
//        if (dynamicMenu && dynamicNavigationLookupTable.getArchived() == 0) {
//          skippable = true;
//        }
//        break;
//      case Navigation.TRASH:
//        if (dynamicMenu && dynamicNavigationLookupTable.getTrashed() == 0) {
//          skippable = true;
//        }
//        break;
//    }
//    return skippable;
//  }

//  private boolean checkSkippableItem(int i) {
//    boolean dynamicMenu = Prefs.getBoolean(PREF_DYNAMIC_MENU, true);
//    DynamicNavigationLookupTable dynamicNavigationLookupTable = null;
//    if (dynamicMenu) {
//      dynamicNavigationLookupTable = DynamicNavigationLookupTable.getInstance();
//    }
//
//    return getSkippableForNavItem(i, dynamicMenu, dynamicNavigationLookupTable);
//  }
//
//  private boolean getSkippableForNavItem(int i, boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
//    switch (i) {
//      case Navigation.REMINDERS:
//        return isRemindersSkippable(dynamicMenu, dynamicNavigationLookupTable);
//      case Navigation.UNCATEGORIZED:
//        return isUncategorizedSkippable(dynamicMenu, dynamicNavigationLookupTable);
//      case Navigation.ARCHIVE:
//        return isArchiveSkippable(dynamicMenu, dynamicNavigationLookupTable);
//      case Navigation.TRASH:
//        return isTrashSkippable(dynamicMenu, dynamicNavigationLookupTable);
//      default:
//        return false;
//    }
//  }
//  private boolean isRemindersSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
//    return (dynamicMenu && dynamicNavigationLookupTable.getReminders() == 0) ;
//  }
//
//  private boolean isUncategorizedSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
//    boolean showUncategorized = Prefs.getBoolean(PREF_SHOW_UNCATEGORIZED, false);
//    return (!showUncategorized || (dynamicMenu && dynamicNavigationLookupTable.getUncategorized() == 0));
//  }
//
//  private boolean isArchiveSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
//    return (dynamicMenu && dynamicNavigationLookupTable.getArchived() == 0);
//  }
//
//  private boolean isTrashSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
//    return (dynamicMenu && dynamicNavigationLookupTable.getTrashed() == 0) ;
//  }


  private static final Map<Integer, BiFunction<Boolean, DynamicNavigationLookupTable, Boolean>> NAVIGATION_MAP = new HashMap<>();
  static {
    NAVIGATION_MAP.put(Navigation.REMINDERS, MainMenuTask::isRemindersSkippable);
    NAVIGATION_MAP.put(Navigation.UNCATEGORIZED, MainMenuTask::isUncategorizedSkippable);
    NAVIGATION_MAP.put(Navigation.ARCHIVE, MainMenuTask::isArchiveSkippable);
    NAVIGATION_MAP.put(Navigation.TRASH, MainMenuTask::isTrashSkippable);
  }

  private boolean checkSkippableItem(int i) {
    boolean dynamicMenu = Prefs.getBoolean(PREF_DYNAMIC_MENU, true);
    DynamicNavigationLookupTable dynamicNavigationLookupTable = null;
    if (dynamicMenu) {
      dynamicNavigationLookupTable = DynamicNavigationLookupTable.getInstance();
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return NAVIGATION_MAP.getOrDefault(i, (b, t) -> false).apply(dynamicMenu, dynamicNavigationLookupTable);
    }
    return false;
  }

  private static boolean isRemindersSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
    return (dynamicMenu && dynamicNavigationLookupTable.getReminders() == 0);
  }

  private static boolean isUncategorizedSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
    boolean showUncategorized = Prefs.getBoolean(PREF_SHOW_UNCATEGORIZED, false);
    return (!showUncategorized || (dynamicMenu && dynamicNavigationLookupTable.getUncategorized() == 0));
  }

  private static boolean isArchiveSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
    return (dynamicMenu && dynamicNavigationLookupTable.getArchived() == 0);
  }

  private static boolean isTrashSkippable(boolean dynamicMenu, DynamicNavigationLookupTable dynamicNavigationLookupTable) {
    return (dynamicMenu && dynamicNavigationLookupTable.getTrashed() == 0);
  }


}
