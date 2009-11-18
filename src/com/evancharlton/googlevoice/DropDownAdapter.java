package com.evancharlton.googlevoice;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;

public class DropDownAdapter implements ListAdapter, SpinnerAdapter {
	private SpinnerAdapter mAdapter;

	public DropDownAdapter(SpinnerAdapter adapter) {
		this.mAdapter = adapter;
	}

	public int getCount() {
		return mAdapter == null ? 0 : mAdapter.getCount();
	}

	public Object getItem(int position) {
		return mAdapter == null ? null : mAdapter.getItem(position);
	}

	public long getItemId(int position) {
		return mAdapter == null ? -1 : mAdapter.getItemId(position);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		return getDropDownView(position, convertView, parent);
	}

	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return mAdapter == null ? null : mAdapter.getDropDownView(position, convertView, parent);
	}

	public boolean hasStableIds() {
		return mAdapter != null && mAdapter.hasStableIds();
	}

	public void registerDataSetObserver(DataSetObserver observer) {
		if (mAdapter != null) {
			mAdapter.registerDataSetObserver(observer);
		}
	}

	public void unregisterDataSetObserver(DataSetObserver observer) {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(observer);
		}
	}

	public boolean areAllItemsEnabled() {
		return true;
	}

	public boolean isEnabled(int position) {
		return true;
	}

	public int getItemViewType(int position) {
		return 0;
	}

	public int getViewTypeCount() {
		return 1;
	}

	public boolean isEmpty() {
		return getCount() == 0;
	}
}