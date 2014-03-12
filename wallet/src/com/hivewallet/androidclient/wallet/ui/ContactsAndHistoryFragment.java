package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.hivewallet.androidclient.wallet_test.R;

import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ContactsAndHistoryFragment extends SherlockFragment
{
	FragmentTabHost chFragmentTabHost;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.contacts_and_history_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		chFragmentTabHost = (FragmentTabHost)getActivity().findViewById(android.R.id.tabhost);
		chFragmentTabHost.setup(getActivity(), getChildFragmentManager(), android.R.id.tabcontent);

		chFragmentTabHost.addTab(chFragmentTabHost.newTabSpec("contacts").setIndicator(
				"", getResources().getDrawable(R.drawable.ic_menu_allfriends)),
				ContactsFragment.class, null);

		chFragmentTabHost.addTab(chFragmentTabHost.newTabSpec("history").setIndicator(
				"", getResources().getDrawable(R.drawable.ic_menu_recent_history)),
				TransactionsListFragment.class, null);

	}
}
