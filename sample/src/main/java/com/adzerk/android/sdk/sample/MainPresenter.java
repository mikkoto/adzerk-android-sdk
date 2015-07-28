package com.adzerk.android.sdk.sample;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.adzerk.android.sdk.AdzerkSdk;
import com.adzerk.android.sdk.AdzerkSdk.ResponseListener;
import com.adzerk.android.sdk.rest.Content;
import com.adzerk.android.sdk.rest.Decision;
import com.adzerk.android.sdk.rest.Placement;
import com.adzerk.android.sdk.rest.Request;
import com.adzerk.android.sdk.rest.Response;
import com.adzerk.android.sdk.sample.VikingGenerator.Quote;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit.RetrofitError;

public class MainPresenter {
    static final long NETWORK_ID = 9792L;
    static final long SITE_ID = 306998L;
    static final int FLIGHT_ID = 699801; // images only

    static final int VIKING_COUNT = 20;
    static final int AD_MODULUS = 5;

    MainModel model;
    MainView view;

    public MainPresenter(MainModel model, MainView view) {
        this.model = model;
        this.view = view;
        view.setAdapter(new QuotesAdapter(
                new VikingGenerator(VIKING_COUNT),
                AD_MODULUS,
                AdzerkSdk.getInstance()
        ));
    }

    @Subscribe
    public void OnAdClick(AdClickEvent event) {
        Activity activity = view.getActivity();
        if (activity != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(event.url));
            activity.startActivity(intent);
        }
    }

    public static class QuotesAdapter extends RecyclerView.Adapter<QuotesAdapter.ViewHolder> {
        static final String TAG = QuotesAdapter.class.getSimpleName();

        static final int CONTENT_CARD_VIEW_TYPE = 1;
        static final int AD_CARD_VIEW_TYPE = 2;

        VikingGenerator generator;
        int adModulus;
        AdzerkSdk sdk;

        public QuotesAdapter(VikingGenerator generator, int adModulus, AdzerkSdk sdk) {
            this.generator = generator;
            this.adModulus = adModulus;
            this.sdk = sdk;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case CONTENT_CARD_VIEW_TYPE:
                    return new ContentViewHolder(inflater.inflate(R.layout.item_quote_card, parent, false));

                case AD_CARD_VIEW_TYPE:
                    return new AdViewHolder(inflater.inflate(R.layout.item_quote_card, parent, false));

                default:
                    throw new IllegalArgumentException("Unsupported view type");
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder vh, int position) {

            switch (vh.getItemViewType()) {
                case CONTENT_CARD_VIEW_TYPE:
                    ContentViewHolder holder = (ContentViewHolder) vh;
                    int quotePosition = position - position / adModulus;
                    Quote q = generator.getQuote(quotePosition);
                    holder.txtName.setText(q.name);
                    holder.txtQuote.setText(q.quote);
                    setHeadShot(holder.imgHeadShot, q.url);
                    break;

                case AD_CARD_VIEW_TYPE:
                    final AdViewHolder adViewHolder = (AdViewHolder) vh;
                    sdk.request(
                            new Request.Builder()
                                  .addPlacement(new Placement("div1", NETWORK_ID, SITE_ID, 5).setFlightId(FLIGHT_ID))
                                  .build(),
                            new ResponseListener() {
                                @Override
                                public void success(Response response) {
                                    Decision decision = response.getDecision("div1");
                                    Content content = decision.getContents().get(0);

                                    // set the click through url:
                                    adViewHolder.setClickUrl(decision.getClickUrl());
                                    loadAdContent(adViewHolder,
                                          content,
                                          decision.getImpressionUrl());

                                    // display 'title' in name field
                                    Log.d(TAG, "Title: " + content.getTitle());
                                    adViewHolder.txtName.setText(content.getTitle());

                                    // display 'quote' from a customData returned with the ad content:
                                    Object q = content.getCustomData("quote");
                                    if (q != null) {
                                        Log.d(TAG, "Quote: " + q.toString());
                                        adViewHolder.txtQuote.setText(q.toString());
                                    } else {
                                        adViewHolder.txtQuote.setText("Quote unavailable");
                                    }

                                }

                                @Override
                                public void error(RetrofitError error) {
                                    Log.d(TAG, "Error: " + error.getMessage());
                                }
                            }
                    );

                default:
                    break;
            }
        }

        private void loadAdContent(AdViewHolder vh, Content content, final String impressionUrl) {
            if (content.isImage()) {
                ImageView imgView = vh.imgAd;
                Picasso.with(imgView.getContext())
                        .load(content.getImageUrl())
                        .into(imgView, new Callback() {
                            @Override
                            public void onSuccess() {
                                sdk.impression(impressionUrl);
                            }

                            @Override
                            public void onError() {
                                Log.d(TAG, "Ignoring ad load error");
                            }
                        });
            }
        }

        private void setHeadShot(ImageView imgView, String url) {
            Log.d(TAG, "Loading headshot from url: " + url);
            Picasso.with(imgView.getContext())
                    .load(url)
                    // TODO: add placeholder image - .placeholder(R.drawable ...
                  .into(imgView);
        }

        @Override
        public int getItemCount() {
            int contentCount = generator.getCount();                // content list items
            if (adModulus > 1) {
                contentCount += generator.getCount() / adModulus;   // ad list items
            }
            return contentCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 || adModulus == 0) {
                // position === 0  => Ads are never at top of list
                // adModulus == 0  => Never show ads
                return CONTENT_CARD_VIEW_TYPE;
            }

            if (adModulus == 1) {
                return AD_CARD_VIEW_TYPE;
            }

            // Every nth card is an Ad
            return (position + 1) % adModulus == 0 ? AD_CARD_VIEW_TYPE : CONTENT_CARD_VIEW_TYPE;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View itemView) {
                super(itemView);
            }
        }

        public static class ContentViewHolder extends ViewHolder {
            @Bind(R.id.head_shot) ImageView imgHeadShot;
            @Bind(R.id.name) TextView txtName;
            @Bind(R.id.quote) TextView txtQuote;

            public ContentViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);

                if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    imgHeadShot.setClipToOutline(true);
                    imgHeadShot.setOutlineProvider(new RoundedAvatarProvider());
                }
            }
        }

        public static class AdViewHolder extends ViewHolder {
            @Bind(R.id.head_shot) ImageView imgAd;
            @Bind(R.id.name) TextView txtName;
            @Bind(R.id.quote) TextView txtQuote;
            @Bind(R.id.sponsored) TextView txtSponsored;
            String clickUrl;

            public AdViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this, itemView);

                // show indicator that item is a sponsored ad
                txtSponsored.setVisibility(View.VISIBLE);

                if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    imgAd.setClipToOutline(true);
                    imgAd.setOutlineProvider(new RoundedAvatarProvider());
                }

                this.clickUrl = null;
            }

            public void setClickUrl(String clickUrl) {
                this.clickUrl = clickUrl;
            }

            @OnClick(R.id.card_view)
            public void onClick() {
                if (clickUrl != null) {
                    BusProvider.post(new AdClickEvent(clickUrl));
                }
            }
        }
    }

    public static class AdClickEvent {
        String url;

        public AdClickEvent(String url) {
            this.url = url;
        }
    }
}

