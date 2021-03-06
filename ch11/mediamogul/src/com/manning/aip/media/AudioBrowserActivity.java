package com.manning.aip.media;

import static android.provider.BaseColumns._ID;
import static android.provider.MediaStore.Audio.AudioColumns.ARTIST;
import static android.provider.MediaStore.Audio.AudioColumns.IS_MUSIC;
import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static android.provider.MediaStore.MediaColumns.DATA;
import static android.provider.MediaStore.MediaColumns.TITLE;

import java.util.HashSet;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

/**
 * Queries MediaStore ContentProvider to find all of the songs on the device
 * @author Michael Galpin
 *
 */
public class AudioBrowserActivity extends Activity {

	// the song the user picked
	private Song selectedSong;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.audio_browser);
		// setup UI
		AudioListAdapter adapter = new AudioListAdapter();
		ListView songList = (ListView) findViewById(R.id.list);
		songList.setAdapter(adapter);
		// button for going to next
		Button next = (Button) findViewById(R.id.nxtBtn2);
		next.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(AudioBrowserActivity.this, 
						VideoChooserActivity.class);
				// copy data sent in
				intent.putExtras(getIntent());
				intent.putExtra("selectedSong", selectedSong);
				startActivity(intent);
			}
			
		});
	}

	/**
	 * Custom adapter backed by a Cursor created by querying for all of the
	 * music on the device
	 */
	private class AudioListAdapter extends BaseAdapter{
		private Cursor cursor;
		private Activity activity = AudioBrowserActivity.this;
		// the row in the list of the song the user picked
		private RadioButton selectedRow;
		// songs that are currently playing
		private final HashSet<Long> playingSongs = new HashSet<Long>();
		
		public AudioListAdapter(){
			super();
			String[] columns = {TITLE,ARTIST,_ID, DATA};
			// where clause so we don't get ringtones, podcasts, etc.
			String whereClause = IS_MUSIC + " = ?";
			String[] whereValues = {"1"};
			cursor = managedQuery(EXTERNAL_CONTENT_URI,
				columns,
				whereClause,
				whereValues,
				null
			);
		}
		
		@Override
		public int getCount() {
			return cursor.getCount();
		}

		// reads the data out of the Cursor and constructs a Song
		@Override
		public Object getItem(int position) {
			Song song = new Song();
			cursor.moveToPosition(position);
			song.title = cursor.getString(cursor.getColumnIndex(TITLE));
			song.artist = cursor.getString(cursor.getColumnIndex(ARTIST));
			song.id = cursor.getLong(cursor.getColumnIndex(_ID));
			song.setUri(cursor.getString(cursor.getColumnIndex(DATA)));
			return song;
		}

		@Override
		public long getItemId(int position) {
			return ((Song) getItem(position)).id;
		}

		@Override
		public View getView(int position, View row, ViewGroup parent) {
			if (row == null){
				LayoutInflater airPump = activity.getLayoutInflater();
				row = airPump.inflate(R.layout.audio_list_item, parent, false);
			}
			RowViewHolder holder = (RowViewHolder) row.getTag();
			if (holder == null){
				holder = new RowViewHolder(row);
				row.setTag(holder);
			}
			final Song song = (Song) getItem(position);
			TextView songLabel = holder.songLabel;
			songLabel.setText(song.toString());
			final Button playBtn = holder.playBtn;
			if (playingSongs.contains(song.id)){
				playBtn.setText(R.string.pause);
			} else {
				playBtn.setText(R.string.play);
			}
			// preview the song, listen to the first 15 seconds
			playBtn.setOnClickListener(new OnClickListener(){
				private Handler handler = new Handler();
				MediaPlayer player = null;
				long maxTime = 15L*1000; // 15 seconds
				long timeLeft = maxTime;
				Runnable autoStop;
				@Override
				public void onClick(View view) {
					if (player == null){
						player = MediaPlayer.create(activity, song.uri);
					}
					// handle pausing/resume
					if (!playingSongs.contains(song.id)){
						player.start();
						playingSongs.add(song.id);
						// timer so you can only listen to 15 seconds
						autoStop = new Runnable(){
							@Override
							public void run() {
								player.pause();
								player.seekTo(0);
								playingSongs.remove(song.id);
								playBtn.setText(R.string.play);
								timeLeft = maxTime;
							}
						};
						handler.postDelayed(autoStop, timeLeft);					
						playBtn.setText(R.string.pause);
					} else {
						player.pause();
						playingSongs.remove(song.id);
						// keep track of where they paused and how much time 
						// left on the timer
						timeLeft = maxTime - player.getCurrentPosition();
						playBtn.setText(R.string.play);
						handler.removeCallbacks(autoStop);
					}
				}
				
			});
			// radio button for selecting the song to be used
			final RadioButton radio = holder.radio;
			radio.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					if (selectedRow != null){
						selectedRow.setChecked(false);
					}
					selectedRow = radio;
					selectedSong = song;
				}
			});
			// remember the state as view is recycled
			if (selectedSong != null && song.id == selectedSong.id){
				radio.setChecked(true);
			} else {
				radio.setChecked(false);
			}
			return row;
		}		
	}
	
	/**
	 * View holder pattern for the list adapter
	 */
	private static class RowViewHolder{
		final TextView songLabel;
		final Button playBtn;
		final RadioButton radio;
		RowViewHolder(View row){
			songLabel = (TextView) row.findViewById(R.id.song);
			playBtn = (Button) row.findViewById(R.id.playBtn);
			radio = (RadioButton) row.findViewById(R.id.rbtn);
		}
	}
	
	/**
	 * Simple data structure representing a song. This is a Parcelable so we
	 * can pass it to the next Activity.
	 */
	public static class Song implements Parcelable{
		String title;
		String artist;
		long id;
		Uri uri;
		
		public void setUri(String uriString){
			uri = new Uri.Builder().appendPath(uriString).build();
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder();
			sb.append(title).append(" -- ");
			if (artist != null){
				sb.append(artist);
			} else {
				sb.append("Unknwon Artist");
			}
			return sb.toString();
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel parcel, int flags) {
			parcel.writeString(title);
			parcel.writeString(artist);
			parcel.writeLong(id);
			Uri.writeToParcel(parcel, uri);
		}
		
		public static final Parcelable.Creator<Song> CREATOR = 
			new Parcelable.Creator<Song>() {

			@Override
			public Song createFromParcel(Parcel source) {
				Song song = new Song();
				song.title = source.readString();
				song.artist = source.readString();
				song.id = source.readLong();
				song.uri = Uri.CREATOR.createFromParcel(source);
				return song;
			}

			@Override
			public Song[] newArray(int size) {
				return new Song[size];
			}
			
		};
	}
}
