/*
 * Copyright 2013-2014 the original author or authors.
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

package com.hivewallet.androidclient.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.hivewallet.androidclient.wallet.R;

public final class EndOfLifeNoticeFragment extends Fragment
{
	private Activity activity;

	private TextView messageView;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		messageView = (TextView) inflater.inflate(R.layout.end_of_life_notice_fragment, container);
		messageView.setText(Html.fromHtml(getString(R.string.end_of_life_notice)));
		
		messageView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final Intent intent = new Intent(activity, EndOfLifeActivity.class);
				startActivity(intent);
			}
		});

		return messageView;
	}
}
