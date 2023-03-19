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

package it.feio.android.omninotes.helpers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import exceptions.ImportException;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.helpers.notifications.NotificationChannels.NotificationChannelNames;
import it.feio.android.omninotes.helpers.notifications.NotificationsHelper;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Category;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.utils.GeocodeHelper;
import it.feio.android.omninotes.utils.ReminderHelper;
import it.feio.android.omninotes.utils.StorageHelper;
import it.feio.android.springpadimporter.Importer;
import it.feio.android.springpadimporter.models.SpringpadAttachment;
import it.feio.android.springpadimporter.models.SpringpadComment;
import it.feio.android.springpadimporter.models.SpringpadElement;
import it.feio.android.springpadimporter.models.SpringpadItem;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface SpringpadElementHandler {
  void handle(SpringpadElement element, StringBuilder content);
}

interface AttachmentObject {
  Uri uri = null;
  Attachment attachement = null;
}


public class SpringImportHelper {

  public static final String ACTION_DATA_IMPORT_SPRINGPAD = "action_data_import_springpad";
  public static final String EXTRA_SPRINGPAD_BACKUP = "extra_springpad_backup";
  private Context context = null;

  private Map<String, SpringpadElementHandler> notesTypes = new HashMap<>();

  private int importedSpringpadNotes, importedSpringpadNotebooks;

  public SpringImportHelper(Context context) {
    this.context = context;
    this.createNotesTypes();
  }

  private void createNotesTypes() {
    notesTypes.put(SpringpadElement.TYPE_TVSHOW, (e, c) -> c.append(System.getProperty("line.separator"))
            .append(TextUtils.join(", ", e.getCast())));

    notesTypes.put(SpringpadElement.TYPE_BOOK, (e, c) -> c.append(System.getProperty("line.separator"))
            .append("Author: ")
            .append(e.getAuthor())
            .append(System.getProperty("line.separator"))
            .append("Publication date: ")
            .append(e.getPublicationDate()));

    notesTypes.put(SpringpadElement.TYPE_RECIPE, (e, c) -> c.append(System.getProperty("line.separator"))
            .append("Ingredients: ")
            .append(e.getIngredients())
            .append(System.getProperty("line.separator"))
            .append("Directions: ")
            .append(e.getDirections()));

    notesTypes.put(SpringpadElement.TYPE_BOOKMARK, (e, c) -> c.append(System.getProperty("line.separator"))
            .append(e.getUrl()));

    notesTypes.put(SpringpadElement.TYPE_BUSINESS, (e, c) -> {
      if (e.getPhoneNumbers() != null) {
        c.append(System.getProperty("line.separator"))
                .append("Phone number: ")
                .append(e.getPhoneNumbers().getPhone());
      }
    });

    notesTypes.put(SpringpadElement.TYPE_PRODUCT, (e, c) -> c.append(System.getProperty("line.separator"))
            .append("Category: ")
            .append(e.getCategory())
            .append(System.getProperty("line.separator"))
            .append("Manufacturer: ")
            .append(e.getManufacturer())
            .append(System.getProperty("line.separator"))
            .append("Price: ")
            .append(e.getPrice()));

    notesTypes.put(SpringpadElement.TYPE_WINE, (e, c) -> c.append(System.getProperty("line.separator"))
            .append("Wine type: ")
            .append(e.getWine_type())
            .append(System.getProperty("line.separator"))
            .append("Varietal: ")
            .append(e.getVarietal())
            .append(System.getProperty("line.separator"))
            .append("Price: ")
            .append(e.getPrice()));

    notesTypes.put(SpringpadElement.TYPE_ALBUM, (e, c) -> c.append(System.getProperty("line.separator"))
            .append("Artist: ")
            .append(e.getArtist()));


  }

  /**
   * Imports notes and notebooks from Springpad exported archive
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public synchronized void importDataFromSpringpad(Intent intent, NotificationsHelper mNotificationsHelper) {
    Importer importer = new Importer();
    importArchive(intent, importer, mNotificationsHelper);
    List<SpringpadElement> elements = importer.getSpringpadNotes();

    if (elements == null || elements.isEmpty())     // If nothing is retrieved it will exit
      return;

    HashMap<String, Category> categoriesWithUuid = createCategoryMaps(importer, mNotificationsHelper);
    Category defaultCategory = createDefaultCategory();

    for (SpringpadElement springpadElement : importer.getNotes()) {
      Note createdNote = createNote(springpadElement, new Note());
      handleTags(springpadElement, createdNote);
      handleAddress(springpadElement, createdNote);
      handleImage(springpadElement, createdNote);
      handleOtherAttachment(springpadElement, createdNote);

      // Creation, modification, category
      createdNote.setCreation(springpadElement.getCreated().getTime());
      createdNote.setLastModification(springpadElement.getModified().getTime());

      // If the note has a category is added to the map to be post-processed
      if (!springpadElement.getNotebooks().isEmpty()) {
        createdNote.setCategory(categoriesWithUuid.get(springpadElement.getNotebooks().get(0)));
      } else {
        createdNote.setCategory(defaultCategory);
      }

      // The note is saved
      DbHelper.getInstance().updateNote(createdNote, false);
      ReminderHelper.addReminder(context, createdNote);

      // Updating notification
      importedSpringpadNotes++;
      updateImportNotification(importer, mNotificationsHelper);
    }

    deleteTemporaryData(importer);
  }

  private void deleteTemporaryData(Importer importer){
    // Delete temp data
    try {
      importer.clean();
    } catch (IOException e) {
      LogDelegate.w("Springpad import temp files not deleted");
    }
  }
  private void importArchive(Intent intent, Importer importer,
                             NotificationsHelper mNotificationsHelper) {
    String backupPath = intent.getStringExtra(EXTRA_SPRINGPAD_BACKUP);
    try {
      importer.setZipProgressesListener(percentage -> mNotificationsHelper.setMessage(context
              .getString(R.string.extracted) + " " + percentage + "%").show());
      importer.doImport(backupPath);
      // Updating notification
      updateImportNotification(importer, mNotificationsHelper);
    } catch (ImportException e) {
      new NotificationsHelper(context)
              .createStandardNotification(NotificationChannelNames.BACKUPS,
                      R.drawable.ic_emoticon_sad_white_24dp,
                      context.getString(R.string.import_fail) +
                              ": " + e.getMessage(), null).setLedActive()
              .show();
      return;
    }
  }

  private Category setCategory(String name) {
    Category cat = new Category();
    cat.setName(name);
    cat.setColor(String.valueOf(Color.parseColor("#F9EA1B")));
    DbHelper.getInstance().updateCategory(cat);
    return cat;
  }

  private Category createDefaultCategory() {
    return setCategory("Springpad");
  }

  private HashMap<String, Category> createCategoryMaps(Importer importer,
                                                       NotificationsHelper mNotificationsHelper) {
    HashMap<String, Category> categoriesWithUuid = new HashMap<>();
    for (SpringpadElement springpadElement : importer.getNotebooks()) {
      Category cat = setCategory(springpadElement.getName());
      categoriesWithUuid.put(springpadElement.getUuid(), cat);

      // Updating notification
      importedSpringpadNotebooks++;
      updateImportNotification(importer, mNotificationsHelper);
    }
    return categoriesWithUuid;
  }

  private Note createNote(SpringpadElement springpadElement, Note note) {
    note.setTitle(springpadElement.getName());

    // Content dependent from type of Springpad note
    StringBuilder content = new StringBuilder();

    content.append( TextUtils.isEmpty(springpadElement.getText()) ? "" : Html.fromHtml(springpadElement.getText()));
    content.append(TextUtils.isEmpty(springpadElement.getDescription()) ? "" : springpadElement.getDescription());

    // Some notes could have been exported wrongly
    if (springpadElement.getType() == null) {
      Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show();
    }

    // Description
    if (notesTypes.containsKey(springpadElement.getType())) {
      notesTypes.get(springpadElement.getType()).handle(springpadElement, content);
    }

    if (springpadElement.getDate() != null) {
      note.setAlarm(springpadElement.getDate().getTime());
    }

    for (SpringpadComment springpadComment : springpadElement.getComments()) {
      content.append(System.getProperty("line.separator")).append(springpadComment.getCommenter())
              .append(" commented at 0").append(springpadComment.getDate()).append(": ")
              .append(springpadElement.getArtist());
    }

    note.setContent(content.toString());

    return note;
  }

  private void handleTags(SpringpadElement springpadElement, Note createdNote) {
    String tags = springpadElement.getTags().size() > 0 ? "#"
            + TextUtils.join(" #", springpadElement.getTags()) : "";
    if (createdNote.isChecklist()) {
      createdNote.setTitle(createdNote.getTitle() + tags);
    } else {
      createdNote.setContent(createdNote.getContent() + System.getProperty("line.separator") + tags);
    }
  }

  private void handleAddress(SpringpadElement springpadElement, Note createdNote) {
    String address =
            springpadElement.getAddresses() != null ? springpadElement.getAddresses().getAddress()
                    : "";
    if (!TextUtils.isEmpty(address)) {
      try {
        double[] coords = GeocodeHelper.getCoordinatesFromAddress(context, address);
        createdNote.setLatitude(coords[0]);
        createdNote.setLongitude(coords[1]);
      } catch (IOException e) {
        LogDelegate.e("An error occurred trying to resolve address to coords during Springpad " +
                "import");
      }
      createdNote.setAddress(address);
    }
  }

  private void handleImage(SpringpadElement springpadElement,
                           Note createdNote) {
    String image = springpadElement.getImage();
    Attachment mAttachment = null;
    Uri uri = null;
    if (!TextUtils.isEmpty(image)) {
      try {
        File file = StorageHelper.createNewAttachmentFileFromHttp(context, image);
        uri = Uri.fromFile(file);
        String mimeType = StorageHelper.getMimeType(uri.getPath());
        mAttachment = new Attachment(uri, mimeType);
      } catch (MalformedURLException e) {
        mAttachment = StorageHelper.createAttachmentFromUri(context, uri, true);
      } catch (IOException e) {
        LogDelegate.e("Error retrieving Springpad online image");
      }
      if (mAttachment != null) {
        createdNote.addAttachment(mAttachment);
      }
      mAttachment = null;
    }
  }

  private void handleOtherAttachment(SpringpadElement springpadElement,
                           Note createdNote) {
    Attachment mAttachment = null;
    Uri uri = null;
    for (SpringpadAttachment springpadAttachment : springpadElement.getAttachments()) {
      // The attachment could be the image itself so it's jumped

      if (TextUtils.isEmpty(springpadAttachment.getUrl())) {
        continue;
      }

      // Tries first with online images
      try {
        File file = StorageHelper
                .createNewAttachmentFileFromHttp(context, springpadAttachment.getUrl());
        uri = Uri.fromFile(file);
        String mimeType = StorageHelper.getMimeType(uri.getPath());
        mAttachment = new Attachment(uri, mimeType);
      } catch (MalformedURLException e) {
        mAttachment = StorageHelper.createAttachmentFromUri(context, uri, true);
      } catch (IOException e) {
        LogDelegate.e("Error retrieving Springpad online image");
      }
      if (mAttachment != null) {
        createdNote.addAttachment(mAttachment);
      }
      mAttachment = null;
    }
  }

  private void handleReminder(SpringpadElement springpadElement,
                                     Note createdNote) {
    Attachment mAttachment = null;
    Uri uri = null;
    for (SpringpadAttachment springpadAttachment : springpadElement.getAttachments()) {
      // The attachment could be the image itself so it's jumped

      if (TextUtils.isEmpty(springpadAttachment.getUrl())) {
        continue;
      }

      // Tries first with online images
      try {
        File file = StorageHelper
                .createNewAttachmentFileFromHttp(context, springpadAttachment.getUrl());
        uri = Uri.fromFile(file);
        String mimeType = StorageHelper.getMimeType(uri.getPath());
        mAttachment = new Attachment(uri, mimeType);
      } catch (MalformedURLException e) {
        mAttachment = StorageHelper.createAttachmentFromUri(context, uri, true);
      } catch (IOException e) {
        LogDelegate.e("Error retrieving Springpad online image");
      }
      if (mAttachment != null) {
        createdNote.addAttachment(mAttachment);
      }
      mAttachment = null;
    }
  }

    private void updateImportNotification(Importer importer,
      NotificationsHelper mNotificationsHelper) {
    mNotificationsHelper.setMessage(
        importer.getNotebooksCount() + " " + context.getString(R.string.categories) + " ("
            + importedSpringpadNotebooks + " " + context.getString(R.string.imported) + "), "
            + +importer.getNotesCount() + " " + context.getString(R.string.notes) + " ("
            + importedSpringpadNotes + " " + context.getString(R.string.imported) + ")").show();
  }
}
