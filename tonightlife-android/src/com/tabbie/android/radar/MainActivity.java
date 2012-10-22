package com.tabbie.android.radar;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.facebook.android.Facebook;
import com.google.android.apps.analytics.easytracking.EasyTracker;
import com.google.android.apps.analytics.easytracking.TrackedActivity;
import com.google.android.gcm.GCMRegistrar;
import com.tabbie.android.radar.ShareDialogManager.ShareMessageSender;
import com.tabbie.android.radar.adapters.AbstractEventListAdapter;
import com.tabbie.android.radar.adapters.ChronologicalComparator;
import com.tabbie.android.radar.adapters.DefaultComparator;
import com.tabbie.android.radar.core.AbstractFilter;
import com.tabbie.android.radar.core.BasicCallback;
import com.tabbie.android.radar.core.FlingableTabHost;
import com.tabbie.android.radar.core.MultiSpinner.MultiSpinnerListener;
import com.tabbie.android.radar.core.TLJSONParser;
import com.tabbie.android.radar.core.cache.ImageLoader;
import com.tabbie.android.radar.core.facebook.FBPerson;
import com.tabbie.android.radar.core.facebook.FacebookAuthenticator;
import com.tabbie.android.radar.core.facebook.FacebookUserRemoteResource;
import com.tabbie.android.radar.enums.Lists;
import com.tabbie.android.radar.enums.MessageType;
import com.tabbie.android.radar.http.ServerRequest;
import com.tabbie.android.radar.http.ServerResponse;
import com.tabbie.android.radar.http.ServerThreadHandler;
import com.tabbie.android.radar.maps.TLMapActivity;
import com.tabbie.android.radar.model.AbstractListManager;
import com.tabbie.android.radar.model.AbstractViewInflater;
import com.tabbie.android.radar.model.Event;
import com.tabbie.android.radar.model.ShareMessage;
import com.tabbie.android.radar.remote.TonightLifeAuthenticator;

public class MainActivity extends TrackedActivity
	implements OnTabChangeListener,
						 OnItemClickListener,
						 OnItemLongClickListener,
						 ShareMessageSender,
						 Handler.Callback {
	
  public static final String TAG = "MainActivity";
  
  // Request codes
  public static final int REQUEST_EVENT_DETAILS = 40;
  public static final int REQUEST_FACEBOOK = 32665;
  
  // Broadcast Receivers
  private final BroadcastReceiver gcmReceiver = new GCMReceiver();
  
  // Important Server Call and Receive Handlers/Threads
  private final Handler upstreamHandler;
  
  // Inflater objects for adapters
  private AbstractViewInflater<Event> eventInflater;
  private AbstractViewInflater<ShareMessage> messageInflater;
  
  // Managers and feeds
  private final AbstractListManager<Event> listManager = new AbstractListManager<Event>();
  private HashMap<String, ArrayList<ShareMessage>> messageFeed = new HashMap<String, ArrayList<ShareMessage>>();
  private HashMap<String, FBPerson> facebookFriendsMap;
  private ShareDialogManager shareManager;

  // Often-used views
  private TabHost vTabHost;
  private ListView[] vListViews = new ListView[3];

  // Internal state for views
  private Lists currentList = Lists.ALL;
  private int currentViewPosition = Lists.FEATURED.index;

  // FB junk
  private final Facebook facebook = new Facebook("217386331697217");
  private FacebookAuthenticator facebookAuthenticator;
  private FacebookUserRemoteResource facebookUserRemoteResource;
  
  // Tabbie Junk
  private TonightLifeAuthenticator tonightLifeAuthenticator;
  private ArrayList<FBPerson> tabbieFriendsList;
  
  private Dialog currentDialog;
  
  public MainActivity() {
	  super();
	  HandlerThread serverThread = new HandlerThread(TAG + "Thread");
	  serverThread.start();
	  upstreamHandler = new ServerThreadHandler(serverThread.getLooper());
  }
  
  @Override
  public void onCreate(final Bundle savedInstanceState) {
	  
    // Set initial conditions
    super.onCreate(savedInstanceState);
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
	  shareManager = new ShareDialogManager(this);
    
    // Set main XML background
    setContentView(R.layout.main);
    
    // Extend AbstractViewInflater
    createViewInflaters();
    
    // Register the three lists in AbstractLIstManager
    createLists();
    
    // Throw some d's on that bitch
    ((ImageView) findViewById(R.id.loading_spin)).startAnimation(AnimationUtils
        .loadAnimation(this, R.anim.rotate));
    
    // Set initial TabHost conditions
    setupTabHost();
    
    // Instantiate list views
    vListViews[Lists.FEATURED.index] = (ListView) findViewById(R.id.featured_event_list);
    vListViews[Lists.ALL.index] = (ListView) findViewById(R.id.all_event_list);
    vListViews[Lists.LINEUP.index] = (ListView) findViewById(R.id.lineup_event_list);

    // Create and bind adapters
    buildAdapters();
    
  	// Set ListView properties
    for(final ListView v : vListViews) {
    	v.setFastScrollEnabled(true);
    	v.setOnItemClickListener(this);
    	v.setOnItemLongClickListener(this);
    	createTabView(vTabHost, v); 
    }
    
    // Start our authentication chain. First authenticate against facebook
    authenticateFacebook();
  }
  
  public void onTabChanged(final String tabName) {
  	
  	if(tabName.equals(getString(R.string.list_all))) {
  		currentList = Lists.ALL;
  	} else if(tabName.equals(getString(R.string.list_featured))) {
  		currentList = Lists.FEATURED;
  	} else {
  		currentList = Lists.LINEUP;
  	}
	  
  	if(displayEmptyViews()) {
  		return;
  	}
	  
	  final ListView tabView = vListViews[currentList.index];
	  ((BaseAdapter) tabView.getAdapter()).notifyDataSetChanged();
    playAnimation(tabView, getBaseContext(), android.R.anim.fade_in, 100);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    switch (requestCode) {
    
    case REQUEST_FACEBOOK:
      facebook.authorizeCallback(requestCode, resultCode, data);
      break;
      
    case REQUEST_EVENT_DETAILS:
      final Bundle parcelables = data.getExtras();
      final ArrayList<Event> events = parcelables.getParcelableArrayList("events");
      listManager.clear();
      listManager.addAll(events);
      vTabHost.setCurrentTab(currentList.index);
      for (final ListView v : vListViews) {
        final BaseAdapter adapter = (BaseAdapter) v.getAdapter();
        if (adapter != null) {
          adapter.notifyDataSetChanged();
        }
      }
      if(displayEmptyViews()) {
      	break;
      } else {
        vListViews[currentList.index].setSelection(currentViewPosition);
        break;      
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    // LocalBroadcastManager.getInstance(this).registerReceiver(gcmReceiver, new IntentFilter(GCMIntentService.ACTION_REGISTER_GCM));
    facebook.extendAccessTokenIfNeeded(this, null);
  }
  
  @Override
  protected void onPause() {
  	super.onPause();
  	// LocalBroadcastManager.getInstance(this).unregisterReceiver(gcmReceiver);
  }
  
  @Override
	protected void onRestart() {
		super.onRestart();
		
		ServerRequest req = new ServerRequest(MessageType.LOAD_EVENTS, 
				new Handler(this), tonightLifeAuthenticator.getTonightLifeToken());
	  final Message message = Message.obtain();
	  message.obj = req;
	  upstreamHandler.sendMessage(message);
	}

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch(item.getItemId()) {
    
      case R.id.refresh_me:
      	ServerRequest req = new ServerRequest(MessageType.LOAD_EVENTS, new Handler(this), tonightLifeAuthenticator.getTonightLifeToken());
    	  final Message message = Message.obtain();
    	  message.obj = req;
    	  upstreamHandler.sendMessage(message);
    	  break;
    	  
      case R.id.report_me:
    		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
    		emailIntent.setType("plain/text");
    		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, getString(R.array.founders_email));
    		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject));
    		startActivity(Intent.createChooser(emailIntent, "Send feedback..."));
    		break;
		
      case R.id.preference_me:
    		final PreferencesDialog preferencesDialog = new PreferencesDialog(this, R.layout.preferences);
    		preferencesDialog.setTitle("Preferences");
    		preferencesDialog.setOnAgeItemSelectedListener(new OnItemSelectedListener() {
    
    			@Override
    			public void onItemSelected(AdapterView<?> parent, View v,
    					int position, long id) {
    				// TODO Auto-generated method stub
    				
    			}
    
    			@Override
    			public void onNothingSelected(AdapterView<?> container) {
    				// TODO Auto-generated method stub
    				
    			}
    		});
    		preferencesDialog.setOnCostItemsSelectedListener(new MultiSpinnerListener() {
    			
    			@Override
    			public void onItemsSelected(boolean[] selected) {
    				// TODO Auto-generated method stub
    				
    			}
    		});
    		preferencesDialog.setOnEnergyItemsSelectedListener(new MultiSpinnerListener() {
    			
    			@Override
    			public void onItemsSelected(boolean[] selected) {
    				// TODO Auto-generated method stub
    				
    			}
    		});
    		preferencesDialog.show();
    		break;
		
      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }
  
  @Override
  public void onDestroy() {
    super.onDestroy();
    // GCMRegistrar.onDestroy(this);
  }
  
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
		final Event e = (Event) parent.getItemAtPosition(position);
		EasyTracker.getTracker().trackEvent("Event", "Click", e.name, 1);
	  if (null != e) {
	    currentViewPosition = position;
	    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(30);

	    new AsyncTask<Void, Void, Intent>() {
	      private ProgressDialog dialog;

	      @Override
	      protected void onPreExecute() {
	        dialog = ProgressDialog.show(MainActivity.this, "",
	            "Loading, please wait...");
	        super.onPreExecute();
	      }

	      @Override
	      protected Intent doInBackground(Void... params) {
	        Intent intent = new Intent(MainActivity.this,
	            EventDetailsActivity.class);
	        intent.putExtra("eventIndex", listManager.get(currentList.id).indexOf(e));
	        intent.putParcelableArrayListExtra("events", listManager.master);
	        intent.putParcelableArrayListExtra("childList", listManager.get(currentList.id));
	        intent.putExtra("token", tonightLifeAuthenticator.getTonightLifeToken());
	        return intent;
	      }

	      @Override
	      protected void onPostExecute(Intent result) {
	        startActivityForResult(result,
	            REQUEST_EVENT_DETAILS);
	        dialog.dismiss();
	      }
	    }.execute();
	  }
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View v,
			int position, long rowId) {
		/*
		Event longClickedEvent = (Event) parent.getAdapter().getItem(position);
		shareManager.setEventId(longClickedEvent.id);
		
		if(tabbieFriendsList==null) {
			currentDialog = ProgressDialog.show(MainActivity.this, "",
          "Loading, please wait...");
			
			ServerRequest req = new ServerRequest(MessageType.LOAD_FRIENDS, new Handler(this));
			req.mParams.put("auth_token", tonightLifeAuthenticator.getTonightLifeToken());
			req.mParams.put("fb_token", facebook.getAccessToken());
      final Message message = Message.obtain();
      message.obj = req;
      upstreamHandler.sendMessage(message);
		} else {
			shareManager.makeDialog(tabbieFriendsList).show();
		}*/
		return true;
	}

	@Override
	public synchronized boolean handleMessage(final Message msg) {
		Log.d(TAG, "Handling message in Main");
		if(!(msg.obj instanceof ServerResponse)) {
			Log.e(TAG, "msg.obj is not an instance of ServerResponse!");
			return false;
		}
		final ServerResponse resp = (ServerResponse) msg.obj;		
		switch (resp.responseTo) {
		case LOAD_EVENTS:
  		final JSONArray list = resp.parseJsonArray();
  		try {
  			final Set<String> serverLineupIds = 
  					TLJSONParser.parseLineupIds(list.getJSONObject(list.length() - 1));
        listManager.clear();
  			for(int i = 0; i < list.length() - 1; ++i) {
  				Event e = TLJSONParser.parseEvent(list.getJSONObject(i), this, serverLineupIds);
  				listManager.add(e);
  			}
      } catch (final JSONException e) {
	      	e.printStackTrace();
	      	return false;
      } catch (final MalformedURLException e) {
	      	e.printStackTrace();
	      	return false;
      }
		  for (final ListView v : vListViews) {
      		final BaseAdapter adapter = (BaseAdapter) v.getAdapter();
      		if (adapter!=null) {
      			adapter.notifyDataSetChanged();
      		}
      }

      final Bundle starter = getIntent().getExtras();
      if(starter!=null) {
      	Log.d(TAG, "This is the point where GCM would take control of main");
      	// TODO Write actual code
      	
      	// Get a random event
      	Random random = new Random(System.currentTimeMillis());
      	int randomEventIndex = random.nextInt(listManager.get(Lists.ALL.id).size());
      	Event lineupEvent = listManager.master.get(randomEventIndex);
      	lineupEvent.onLineup = true;
      	listManager.refreshList(Lists.LINEUP.id);
      	
      	// TODO Make sure Matt always adds a pushed event to the end user's lineup

			  // TODO This is for testing
			  final ArrayList<ShareMessage> firstEventList = new ArrayList<ShareMessage>();
			  firstEventList.add(new ShareMessage("Justin", "Knutson", "Holy shit balls there's messaging now this is so cool blah blah blah"));
			  firstEventList.add(new ShareMessage("Cesar", "Devers", "Oh baby this event is going to be good. Dr. Dre is going to be there rapping with Tupac and Killa Beez ON DA SWARM"));
			  messageFeed.put(lineupEvent.id, firstEventList);
			  
      	((BaseAdapter) vListViews[Lists.LINEUP.index].getAdapter()).notifyDataSetChanged();
			  currentList = Lists.LINEUP;
      }
      
    	displayEmptyViews();
    	vTabHost.setCurrentTab(currentList.index);
  	  ((ImageView) findViewById(R.id.loading_spin)).clearAnimation();
  	  findViewById(R.id.loading_screen).setVisibility(View.GONE);
  	  findViewById(R.id.tonightlife_layout).setVisibility(View.VISIBLE);
  	  vTabHost.setVisibility(View.VISIBLE);
  	  break;
  	  
		case LOAD_FRIENDS:
			JSONArray friendIds = null;
			try {
				friendIds = ((JSONObject) resp.parseJsonContent()).getJSONArray("friends");
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Set<String> tabbieFriendIds = TLJSONParser.parseFacebookIds(friendIds);
			tabbieFriendsList = new ArrayList<FBPerson>(tabbieFriendIds.size());
			
			if (facebookFriendsMap==null) {
				JSONArray friendsArray;
				try {
					friendsArray = new JSONObject(facebook.request("me/friends")).getJSONArray("data");
					facebookFriendsMap = TLJSONParser.parseFacebookFriendsList(friendsArray);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			for (String id : tabbieFriendIds) {
				Log.d(TAG, "Adding Tabbie Friend " + facebookFriendsMap.get(id).name);
				tabbieFriendsList.add(facebookFriendsMap.get(id));
			}
			
			currentDialog.dismiss();
			currentDialog = null;
			
			shareManager.makeDialog(tabbieFriendsList).show();
			
			break;
		}
	  return true;
	}
	
	private void createViewInflaters() {
    
	  /*
	   * Builder object for displaying views
	   * in the user's Event feed
	   */
	  eventInflater = new AbstractViewInflater<Event>(this, R.layout.list_event) {
	  	private final ImageLoader mLoader = new ImageLoader(mContext);

			@Override
			protected View bindData(Event data, View v) {
		    ((TextView) v.findViewById(R.id.event_text))
		  		  .setText(data.name);
		
		    ((TextView) v
		  		  .findViewById(R.id.event_list_time))
		  		  .setText(data.time.makeYourTime());
		
		    ((TextView) v
		        .findViewById(R.id.event_location))
		        .setText(data.venue);
		    
		    if (data.cost.length() > 0) {
		      ((TextView) v.findViewById(R.id.event_price)).setText(data.cost);
		    }
		    
		    final View viewHolder = v.findViewById(R.id.image_holder);
		    viewHolder.findViewById(R.id.element_loader).startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.rotate));
		    mLoader.displayImage(data.imageUrl.toString(),
		  		  (ImageView) viewHolder.findViewById(R.id.event_image));
				return v;
			}
		};
		
		/*
		 * Builder object for displaying views
		 * in the user's message feed
		 */
	  messageInflater = new AbstractViewInflater<ShareMessage>(this, R.layout.list_message) {
			@Override
			protected View bindData(ShareMessage data, View v) {
				((TextView) v.findViewById(R.id.list_message_name)).setText(data.mUserFirstName + " " + data.mUserLastName + " wrote...");
				((TextView) v.findViewById(R.id.list_message_text)).setText(data.mMessage);
				return v;
			}
	  };
	}
	
	private void registerGCMContent() {
		Log.d(TAG, "Registering GCM Content");
	  GCMRegistrar.checkDevice(this);
    GCMRegistrar.checkManifest(this);
    final String regId = GCMRegistrar.getRegistrationId(this);
    if (regId.equals("")) {
      GCMRegistrar.register(this, "486514846150");
    } else {
      Log.d(TAG, "RegistrationID is: " + regId);
      if(tonightLifeAuthenticator.getTonightLifeToken() != null && !(regId.contentEquals(tonightLifeAuthenticator.getGCMKey()))) {
      	Log.d(TAG, "Putting GCM ID with id " + regId + " and Access Token " + tonightLifeAuthenticator.getTonightLifeToken());
      	
	  		ServerRequest req = new ServerRequest(MessageType.REGISTER_GCM, new Handler(this), regId, tonightLifeAuthenticator.getTonightLifeToken());
	  	  final Message message = Message.obtain();
	  	  message.obj = req;
	  	  upstreamHandler.sendMessage(message);
      }
    }
	}
	
	private void createLists() {
    listManager.createList(Lists.FEATURED.id, new AbstractFilter<Event>() {

			@Override
			public boolean apply(Event o) {
				if(o.isFeatured) {
					return true;
				} else {
					return false;
				}
			}
    	
    });
    
    listManager.createList(Lists.ALL.id, new AbstractFilter<Event>() {

			@Override
			public boolean apply(Event o) {
				return true;
			}
    	
    });
    
    listManager.createList(Lists.LINEUP.id, new AbstractFilter<Event>() {

			@Override
			public boolean apply(Event o) {
				if(o.onLineup) {
					return true;
				} else {
					return false;
				}
			}
    	
    });
	}
	
	private void setupTabHost() {
    vTabHost = (FlingableTabHost) findViewById(android.R.id.tabhost);
    findViewById(R.id.map_button).setOnClickListener(new OnClickListener() {
        public void onClick(View v) {
            Intent intent = new Intent(MainActivity.this, TLMapActivity.class);
            intent.putParcelableArrayListExtra("events", listManager.master);
            intent.putExtra("token", tonightLifeAuthenticator.getTonightLifeToken());
            startActivity(intent);
        }
      });
    vTabHost.setup();
    vTabHost.setOnTabChangedListener(this);
    vTabHost.setCurrentTab(currentList.index);
	}
	
	private void buildAdapters() {
		vListViews[Lists.FEATURED.index].setAdapter(
				new AbstractEventListAdapter<Event>(this,
						listManager.get(Lists.FEATURED.id),
						new DefaultComparator(),
						R.layout.list_container) {
							@Override
							public void buildView(Event source, ViewGroup v) {
								eventInflater.bindView(source, v, v.findViewById(R.id.list_event_container));
							}
				});
		
		vListViews[Lists.ALL.index].setAdapter(
				new AbstractEventListAdapter<Event>(this,
						listManager.get(Lists.ALL.id),
						new DefaultComparator(),
						R.layout.list_container) {
							@Override
							public void buildView(Event source, ViewGroup v) {
								eventInflater.bindView(source, v, v.findViewById(R.id.list_event_container));
							}
				});
		
		vListViews[Lists.LINEUP.index].setAdapter(
				new AbstractEventListAdapter<Event>(this,
						listManager.get(Lists.LINEUP.id),
						new ChronologicalComparator(),
						R.layout.list_container) {

							@Override
							public void buildView(Event source, ViewGroup v) {
								v.removeAllViews();
								eventInflater.bindView(source, v);
								ArrayList<ShareMessage> messageList = messageFeed.get(source.id);
								if(messageList!=null) {
									for(ShareMessage m : messageList) {
										messageInflater.bindView(m, v);
									}
								}
							}
				});
	}
	
	private void authenticateFacebook() {
    ((TextView) findViewById(R.id.loading_text)).setText("Checking Facebook credentials...");
    facebookAuthenticator = new FacebookAuthenticator(facebook, getPreferences(MODE_PRIVATE));
    facebookUserRemoteResource = new FacebookUserRemoteResource(getPreferences(MODE_PRIVATE));
    tonightLifeAuthenticator = new TonightLifeAuthenticator(getPreferences(MODE_PRIVATE));
    facebookAuthenticator.authenticate(this, new BasicCallback<String>() {
    	
      @Override
      public void onFail(String reason) {
        Log.e(this.getClass().getName(), "Facebook auth failed because '" + reason + "'");
        ((TextView) findViewById(R.id.loading_text)).setText("Checking facebook credentials FAILED!");
        throw new RuntimeException(reason);
      }
      
      @Override
      public void onDone(String response) {
        ((TextView) findViewById(R.id.loading_text)).setText("Loading Facebook user information...");
        
        facebookUserRemoteResource.load(upstreamHandler, response, new BasicCallback<String>() {
          @Override
          public void onFail(String reason) {
            Log.e(this.getClass().getName(), "Facebook user info load failed because '" + reason + "'");
            ((TextView) findViewById(R.id.loading_text)).setText("Loading Facebook user information FAILED!");
            throw new RuntimeException(reason);
          }
          
          @Override
          public void onDone(String response) {
            ((TextView) findViewById(R.id.user_name)).setText(response);
            
            tonightLifeAuthenticator.authenticate(upstreamHandler, facebookAuthenticator.getFacebookAccessToken(),
                new BasicCallback<Pair<String,String>>() {
                  @Override
                  public void onDone(Pair<String, String> response) {
                    Log.d(TAG, "Dispatching Request for Events");
                    ServerRequest req = new ServerRequest(MessageType.LOAD_EVENTS, new Handler(MainActivity.this), response.first);
                    final Message message = Message.obtain();
                    message.obj = req;
                    upstreamHandler.sendMessage(message);
                  }

                  @Override
                  public void onFail(String reason) {
                    Log.e(this.getClass().getName(), "TonightLifeAuth failed because '" + reason + "'");
                    ((TextView) findViewById(R.id.loading_text)).setText("TonightLife Auth FAILED!");
                    throw new RuntimeException(reason);
                  }
                });
          }
        });
      }
    });
	}
	
	private class GCMReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent data) {
			Log.d(TAG, "BroadcastReceiver for GCM Received a message");
			String regId = data.getStringExtra("regId");
			if(regId!=null) {
				registerGCMContent();
			}
		}
	}
	
	private boolean displayEmptyViews() {
		findViewById(R.id.radar_list_empty_text).setVisibility(View.GONE);
		findViewById(R.id.lineup_list_empty_text).setVisibility(View.GONE);
		switch(currentList) {
		case ALL:
		case FEATURED:
			if(vListViews[currentList.index].getAdapter().isEmpty()) {
				findViewById(R.id.radar_list_empty_text).setVisibility(View.VISIBLE);
				return true;
			} else {
				return false;
			}
		case LINEUP:
			if(vListViews[currentList.index].getAdapter().isEmpty()) {
				findViewById(R.id.lineup_list_empty_text).setVisibility(View.VISIBLE);
				return true;
			} else {
				return false;
			}
			default:
				return false;
		}
	}
	
  private static Animation playAnimation(View v, Context con, int animationId,
      int StartOffset) {
    if (null != v) {
      Animation animation = AnimationUtils.loadAnimation(con, animationId);
      animation.setStartOffset(StartOffset);
      v.startAnimation(animation);
      return animation;
    }
    return null;
  }
	
	private static final void createTabView(final TabHost host, final ListView view) {
		final String tag = view.getTag().toString();
		final View tabIndicatorView = LayoutInflater.from(host.getContext())
				.inflate(R.layout.tabs_bg, null); 
		((TextView) tabIndicatorView.findViewById(R.id.tabs_text)).setText(tag);
		
	  final TabSpec content = host.newTabSpec(tag)
	      .setIndicator(tabIndicatorView)
	      .setContent(new TabHost.TabContentFactory() {
	        public View createTabContent(final String tag) {
	          return view;
	        }
	      });
	  host.addTab(content);
	}

	@Override
	public void sendMessage(Set<String> ids, String shareMessage, String eventId) {
		ServerRequest req = new ServerRequest(MessageType.POST_MESSAGE, new Handler(this));
		req.mParams.put("auth_token", tonightLifeAuthenticator.getTonightLifeToken());
		req.mParams.put("ids", ids.toString());
		req.mParams.put("message", shareMessage);
		req.mParams.put("event_id", eventId);
    final Message message = Message.obtain();
    message.obj = req;
    upstreamHandler.sendMessage(message);
	}
}