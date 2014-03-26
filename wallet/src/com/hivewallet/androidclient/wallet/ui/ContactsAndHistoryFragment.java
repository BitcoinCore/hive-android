package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.hivewallet.androidclient.wallet_test.R;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.viewpagerindicator.MyTabPageIndicator;
import com.viewpagerindicator.IconPagerAdapter;

public class ContactsAndHistoryFragment extends SherlockFragment
{
	private CAHFragmentPagerAdapter cahFragmentPagerAdapter;
	private ViewPager viewPager;
	private MyTabPageIndicator myTabPageIndicator;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.contacts_and_history_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		cahFragmentPagerAdapter = new CAHFragmentPagerAdapter(getFragmentManager());

		viewPager = (ViewPager)getActivity().findViewById(R.id.vp_tabs);
		viewPager.setAdapter(cahFragmentPagerAdapter);

		myTabPageIndicator = (MyTabPageIndicator)getActivity().findViewById(R.id.tpi_tabs);
		myTabPageIndicator.setViewPager(viewPager);
	}

	private static class CAHFragmentPagerAdapter extends FragmentPagerAdapter implements IconPagerAdapter {
		private static final int NUM_FRAGMENTS = 2;

		public CAHFragmentPagerAdapter(FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public int getCount()
		{
			return NUM_FRAGMENTS;
		}		

		@Override
		public Fragment getItem(int index)
		{
			switch (index) {
				case 0:
					return ContactsFragment.instance();
				case 1:
					return TransactionsListFragment.instance(null);
				default:
					throw new IllegalArgumentException("Unknown fragment index");
			}
		}

		@Override
		public CharSequence getPageTitle(int index) {
			return "";
		}

		@Override
		public int getIconResId(int index) {
			switch (index) {
				case 0:
					return R.drawable.ic_menu_allfriends;
				case 1:
					return R.drawable.ic_menu_recent_history;
				default:
					throw new IllegalArgumentException("Unknown fragment index");
			}			
		}
	}
}
