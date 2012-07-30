package com.tabbie.android.radar;

import java.net.MalformedURLException;
import java.net.URL;

import android.os.Parcel;
import android.os.Parcelable;

public class Event implements Parcelable {
	public final String id, name, description, venueName, address;
	public final URL image;
	public final double lat;
	public final double lon;
	public final boolean featured;
	public final String time;
	
	public int radarCount;
	private boolean onRadar;
	
	public Event(String id, String name, String description, String venueName, String address, URL image, double lat, double lon, int radarCount, boolean featured, String time) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.venueName = venueName;
		this.address = address;
		this.image = image;
		this.lat = lat;
		this.lon = lon;
		this.radarCount = radarCount;
		this.featured = featured;
		this.time = time;
	}

	public boolean isOnRadar() {
		return onRadar;
	}

	public void setOnRadar(boolean onRadar) {
		this.onRadar = onRadar;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {
	  dest.writeString(image.toString());
		dest.writeString(id);
		dest.writeString(name);
		dest.writeString(description);
		dest.writeString(venueName);
		dest.writeString(address);
		dest.writeDouble(lat);
		dest.writeDouble(lon);
		dest.writeInt(radarCount);
		dest.writeInt(featured ? 1 : 0);
		dest.writeString(time);
	}
	
	public static final Parcelable.Creator<Event> CREATOR = new Parcelable.Creator<Event>() {
	  public Event createFromParcel(Parcel in) {
	    String url = in.readString();
	    try {
				return new Event(	in.readString(),
									        in.readString(),
									        in.readString(),
									        in.readString(),
									        in.readString(),
									        new URL(url),
									        in.readDouble(),
									        in.readDouble(),
									        in.readInt(),
									        in.readInt() == 1,
									        in.readString());
			} catch (MalformedURLException e) {
			  e.printStackTrace();
			  return null;
			}
	  }
 
	  public Event[] newArray(int size) {
	    return new Event[size];
	  }
	};
}
