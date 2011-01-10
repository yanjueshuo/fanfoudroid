/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ch_linghu.fanfoudroid;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.ch_linghu.fanfoudroid.TwitterApi.ApiException;
import com.ch_linghu.fanfoudroid.TwitterApi.AuthException;
import com.ch_linghu.fanfoudroid.data.Dm;
import com.ch_linghu.fanfoudroid.data.db.TwitterDbAdapter;
import com.ch_linghu.fanfoudroid.helper.Utils;
import com.ch_linghu.fanfoudroid.ui.base.WithHeaderActivity;
import com.ch_linghu.fanfoudroid.ui.module.TweetEdit;
import com.google.android.photostream.UserTask;

//FIXME: 1. 更换UI后，写私信功能失效，更换新API再测试。2. 和WriteActivity进行整合。
/**
 * 撰写私信界面
 * @author lds
 *
 */
public class WriteDmActivity extends WithHeaderActivity {

	public static final String NEW_TWEET_ACTION = "com.ch_linghu.fanfoudroid.NEW";
	public static final String EXTRA_TEXT = "text";
	public static final String REPLY_ID = "reply_id"; 

	private static final String TAG = "WriteActivity";
	private static final String SIS_RUNNING_KEY = "running";
	private static final String PREFS_NAME = "com.ch_linghu.fanfoudroid";
	
	// View
	private TweetEdit mTweetEdit;
	private EditText mTweetEditText;
	private TextView mProgressText;
	private Button mSendButton;
	private Button backgroundButton;
	private AutoCompleteTextView mToEdit;

	// Task
	private UserTask<Void, Void, TaskResult> mSendTask;
	private FriendsAdapter mFriendsAdapter; // Adapter for To: recipient autocomplete.

	private String _reply_id;
	
	private static final String EXTRA_USER = "user";

	private static final String LAUNCH_ACTION = "com.ch_linghu.fanfoudroid.DMS";
	
	public static Intent createIntent(String user) {
	    Intent intent = new Intent(LAUNCH_ACTION);
	    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

	    if (!Utils.isEmpty(user)) {
	      intent.putExtra(EXTRA_USER, user);
	    }

	    return intent;
	}

	// sub menu
	protected void createInsertPhotoDialog() {

		final CharSequence[] items = { getString(R.string.take_a_picture),
				getString(R.string.choose_a_picture) };

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.insert_picture));
		builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				// Toast.makeText(getApplicationContext(), items[item],
				// Toast.LENGTH_SHORT).show();
				switch (item) {
				case 0:
					openImageCaptureMenu();
					break;
				case 1:
					openPhotoLibraryMenu();
				}
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void getPic(Intent intent, Bundle extras) {
		

	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate.");
		super.onCreate(savedInstanceState);

		// init View
		setContentView(R.layout.write_dm);
		initHeader(HEADER_STYLE_WRITE);
		
		// Intent & Action & Extras
		Intent intent = getIntent();
		String action = intent.getAction();
		Bundle extras = intent.getExtras();

		_reply_id = null;
		
		// View
		mProgressText = (TextView) findViewById(R.id.progress_text);
		mTweetEditText = (EditText) findViewById(R.id.tweet_edit);
		
		TwitterDbAdapter db = getDb();

	    mToEdit = (AutoCompleteTextView) findViewById(R.id.to_edit);
	    Cursor cursor = db.getFollowerUsernames("");
	    // startManagingCursor(cursor);
	    mFriendsAdapter = new FriendsAdapter(this, cursor);
	    mToEdit.setAdapter(mFriendsAdapter);

	    if (extras != null) {
	      String to = extras.getString(EXTRA_USER);
	      if (!Utils.isEmpty(to)) {
	        mToEdit.setText(to);
	        mTweetEdit.requestFocus();
	      }
	    }

		// Update status
		mTweetEdit = new TweetEdit(mTweetEditText,
				(TextView) findViewById(R.id.chars_text));
		mTweetEdit.setOnKeyListener(editEnterHandler);
		mTweetEdit
				.addTextChangedListener(new MyTextWatcher(WriteDmActivity.this));

		mSendButton = (Button) findViewById(R.id.send_button);
		mSendButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				doSend();
			}
		});
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle bundle) {
	    super.onRestoreInstanceState(bundle);

	    mTweetEdit.updateCharsRemain();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "onPause.");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		Log.i(TAG, "onRestart.");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "onResume.");
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i(TAG, "onStart.");
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.i(TAG, "onStop.");
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy.");

		if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
	      // Doesn't really cancel execution (we let it continue running).
	      // See the SendTask code for more details.
	      mSendTask.cancel(true);
	    }
		// Don't need to cancel FollowersTask (assuming it ends properly).

		super.onDestroy();
	}

	private void saveLastEditText() {
		// TODO:
		// Preferences preferences = this.getSharedPreferences(,0);
		// preferences.setStr
		String lastEditText = mTweetEdit.getText();

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("lastEditText", lastEditText);
		editor.commit();
	}

	private void resumeLastEditTexxt() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		String text = settings.getString("lastEditText", "");
		mTweetEdit.setText(text);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mSendTask != null
		        && mSendTask.getStatus() == UserTask.Status.RUNNING) {
	      outState.putBoolean(SIS_RUNNING_KEY, true);
	    }
	}

	public static Intent createNewTweetIntent(String text) {
		Intent intent = new Intent(NEW_TWEET_ACTION);
		intent.putExtra(EXTRA_TEXT, text);

		return intent;
	}

	private class MyTextWatcher implements TextWatcher {

		private WriteDmActivity _activity;

		public MyTextWatcher(WriteDmActivity activity) {
			_activity = activity;
		}

		@Override
		public void afterTextChanged(Editable s) {
			// TODO Auto-generated method stub
			if (s.length() == 0) {
				_activity._reply_id = null;
			}
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			// TODO Auto-generated method stub

		}

	}

	private enum TaskResult {
		    OK, IO_ERROR, AUTH_ERROR, CANCELLED, NOT_FOLLOWED_ERROR
	}

	private void doSend() {
	    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
	      Log.w(TAG, "Already sending.");
	    } else {
	      String to = mToEdit.getText().toString();
	      String status = mTweetEdit.getText().toString();

	      if (!Utils.isEmpty(status) && !Utils.isEmpty(to)) {
	        mSendTask = new SendTask().execute();
	      }
	    }
	} 

	private class SendTask extends UserTask<Void, Void, TaskResult> {
	    @Override
	    public void onPreExecute() {
	      disableEntry();
	      updateProgress("Sending DM...");
	    }

	    @Override
	    public TaskResult doInBackground(Void... params) {
	      try {
	        String user = mToEdit.getText().toString();
	        String text = mTweetEdit.getText().toString();

	        JSONObject jsonObject = getApi().sendDirectMessage(user, text);
	        Dm dm = Dm.create(jsonObject, true);

	        if (!Utils.isEmpty(dm.profileImageUrl)) {
	          // Fetch image to cache.
	          try {
	            getImageManager().put(dm.profileImageUrl);
	          } catch (IOException e) {
	            Log.e(TAG, e.getMessage(), e);
	          }
	        }

	        getDb().createDm(dm, false);
	      } catch (IOException e) {
	        Log.e(TAG, e.getMessage(), e);
	        return TaskResult.IO_ERROR;
	      } catch (AuthException e) {
	        Log.i(TAG, "Invalid authorization.");
	        return TaskResult.AUTH_ERROR;
	      } catch (JSONException e) {
	        Log.w(TAG, "Could not parse JSON after sending update.");
	        return TaskResult.IO_ERROR;
	      } catch (ApiException e) {
	        Log.i(TAG, e.getMessage());
	        // TODO: check is this is actually the case.
	        return TaskResult.NOT_FOLLOWED_ERROR;
	      }

	      return TaskResult.OK;
	    }

	    @Override
	    public void onPostExecute(TaskResult result) {
	      if (isCancelled()) {
	        // Canceled doesn't really mean "canceled" in this task.
	        // We want the request to complete, but don't want to update the
	        // activity (it's probably dead).
	        return;
	      }

	      if (result == TaskResult.AUTH_ERROR) {
	        logout();
	      } else if (result == TaskResult.OK) {
	        mToEdit.setText("");
	        mTweetEdit.setText("");
	        updateProgress("");
	        enableEntry();
	      } else if (result == TaskResult.NOT_FOLLOWED_ERROR) {
	        updateProgress("Unable to send. Is the person following you?");
	        enableEntry();
	      } else if (result == TaskResult.IO_ERROR) {
	        updateProgress("Unable to send");
	        enableEntry();
	      }
	    }
	  }

	  private static class FriendsAdapter extends CursorAdapter {

	    public FriendsAdapter(Context context, Cursor cursor) {
	      super(context, cursor);

	      mInflater = LayoutInflater.from(context);

	      mUserTextColumn = cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER);
	    }

	    private LayoutInflater mInflater;

	    private int mUserTextColumn;

	    @Override
	    public View newView(Context context, Cursor cursor, ViewGroup parent) {
	      View view = mInflater.inflate(R.layout.dropdown_item, parent, false);

	      ViewHolder holder = new ViewHolder();
	      holder.userText = (TextView) view.findViewById(android.R.id.text1);
	      view.setTag(holder);

	      return view;
	    }

	    class ViewHolder {
	      public TextView userText;
	    }

	    @Override
	    public void bindView(View view, Context context, Cursor cursor) {
	      ViewHolder holder = (ViewHolder) view.getTag();

	      holder.userText.setText(cursor.getString(mUserTextColumn));
	    }

	    @Override
	    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
	      String filter = constraint == null ? "" : constraint.toString();

	      return TwitterApplication.mDb.getFollowerUsernames(filter);
	    }

	    @Override
	    public String convertToString(Cursor cursor) {
	      return cursor.getString(mUserTextColumn);
	    }

	    public void refresh() {
	      getCursor().requery();
	    }

	  }
	  
	private void onSendBegin() {
		disableEntry();
		updateProgress(getString(R.string.updateing_status));
		backgroundButton.setVisibility(View.VISIBLE);
	}

	private void onSendSuccess() {
		mTweetEdit.setText("");
		updateProgress(getString(R.string.update_status_success));
		backgroundButton.setVisibility(View.INVISIBLE);
		enableEntry();
		// doRetrieve();
		// draw();
		// goTop();
		try {
			Thread.currentThread().sleep(500);
			updateProgress("");
		} catch (InterruptedException e) {
			Log.i(TAG, e.getMessage());
		}
	}

	private void onSendFailure() {
		updateProgress(getString(R.string.unable_to_update_status));
		enableEntry();
	}

	private void enableEntry() {
		mTweetEdit.setEnabled(true);
		mSendButton.setEnabled(true);
	}

	private void disableEntry() {
		mTweetEdit.setEnabled(false);
		mSendButton.setEnabled(false);
	}

	// UI helpers.

	private void updateProgress(String progress) {
		mProgressText.setText(progress);
	}
	
	private View.OnKeyListener editEnterHandler = new View.OnKeyListener() {
	    public boolean onKey(View v, int keyCode, KeyEvent event) {
	      if (keyCode == KeyEvent.KEYCODE_ENTER
	          || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
	        if (event.getAction() == KeyEvent.ACTION_UP) {
	          doSend();
	        }
	        return true;
	      }
	      return false;
	    }
	};

}