package com.advertise.adgives;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import maes.tech.intentanim.CustomIntent;

public class FragmentWatchAds extends Fragment {
    private CardView cardViewWatchAdsCloseAnyway;
    private TextView textViewAnyway;
    private VideoView videoViewWatchAds;

    private ProgressDialog progressDialog;

    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private FirebaseFirestore firebaseFirestore;

    private ArrayList<Ads> adsArrayList = new ArrayList<>();
    private ArrayList<String> adsUrl = new ArrayList<>();
    private Ads quizads;
    private User user;

    private String state = "";


    private int bottomNavigationController = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.fragment_watchads,container,false);

        cardViewWatchAdsCloseAnyway = view.findViewById(R.id.cardViewWatchAdsCloseAnyway);

        textViewAnyway = view.findViewById(R.id.textViewAnyway);

        videoViewWatchAds = view.findViewById(R.id.videoViewWatchAds);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        firebaseFirestore = FirebaseFirestore.getInstance();

        cardViewWatchAdsCloseAnyway.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {

                videoViewWatchAds.pause();

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setCancelable(false);
                builder.setTitle("CLOSE ANYWAY");
                builder.setMessage("When you leave the ad tracking page, you will not be able to watch ads for 24 hours. Are you sure to do this ?");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        firebaseFirestore.collection("users").document(firebaseUser.getUid()).update("watched",true).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                firebaseFirestore.collection("users").document(firebaseUser.getUid()).update("watchedToday",new ArrayList<>()).addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Intent intent = new Intent(getActivity(),MainActivity.class);
                                        startActivity(intent);
                                        CustomIntent.customType(getContext(),"fadein-to-fadeout");
                                        getActivity().finish();
                                    }
                                });
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Snackbar.make(v,"Failed to log out because credential could not be verified",Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }
                });
                builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        videoViewWatchAds.start();
                    }
                });
                builder.create().show();

            }
        });

        progressDialog = ProgressDialog.show(getContext(),"WATCH ADS","Ad loading please wait",false,false);

        fetchAllAds();

        return view;
    }

    private void fetchAllAds() {
        firebaseFirestore.collection("users").document(firebaseUser.getUid()).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                if (documentSnapshot.exists()){
                    user = documentSnapshot.toObject(User.class);

                    state = getState(user.getLatitude(),user.getLongitude());

                    firebaseFirestore.collection("ads").get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                            for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots){
                                if (documentSnapshot.exists()){
                                    String distance = documentSnapshot.getString("distance");
                                    boolean isPublic = documentSnapshot.getBoolean("isPublic");
                                    double adlatitude = documentSnapshot.getDouble("latitude");
                                    double adlongitude = documentSnapshot.getDouble("longitude");

                                    int km = getBetweenKm(user.getLatitude(),user.getLongitude(),adlatitude,adlongitude);
                                    String adstate = getState(adlatitude,adlongitude);

                                    if (isPublic){
                                        if (distance.equals("Statewide") && state.equals(adstate)){

                                            Ads ads = documentSnapshot.toObject(Ads.class);
                                            adsArrayList.add(ads);
                                            adsUrl.add(ads.getUrl());
                                        }else if (distance.equals("Nationwide")){

                                            Ads ads = documentSnapshot.toObject(Ads.class);
                                            adsArrayList.add(ads);
                                            adsUrl.add(ads.getUrl());
                                        }else if (distance.equals("Local") && km <= 50){
                                            Ads ads = documentSnapshot.toObject(Ads.class);
                                            adsArrayList.add(ads);
                                            adsUrl.add(ads.getUrl());
                                        }

                                    }
                                }
                            }
                            showAds();
                        }
                    });
                }
            }
        });

    }

    private String getState(double latitude, double longitude) {
        String state = "";
        Geocoder geocoder = new Geocoder(getContext());

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude,longitude,1);
            if (geocoder.isPresent()) {
                StringBuilder stringBuilder = new StringBuilder();
                if (addresses.size()>0) {
                    Address returnAddress = addresses.get(0);

                    /*String localityString = returnAddress.getLocality();
                    String name = returnAddress.getFeatureName();
                    String subLocality = returnAddress.getSubLocality();
                    String country = returnAddress.getCountryName();
                    String region_code = returnAddress.getCountryCode();
                    String zipcode = returnAddress.getPostalCode();*/
                    state = returnAddress.getAdminArea();

                }
            } else {

            }
        } catch (IOException e) {
            Toast.makeText(getContext(),"Unfortunately we can't get the state information of the ad",Toast.LENGTH_LONG).show();
        }
        return state;
    }

    private int getBetweenKm(double latitude, double longitude, double adlatitude, double adlongitude) {
        float[] results = new float[1];
        Location.distanceBetween(latitude,longitude,adlatitude,adlongitude,results);
        float resultdistance = results[0];

        int km = (int) (resultdistance/1000);

        return km;
    }

    private void showAds() {

        if (!adsUrl.isEmpty()){
            for (String url : user.getWatchedToday()
            ) {
                adsUrl.remove(url);
            }

            if (user.isWatched()){
                progressDialog.dismiss();
                adsLimit();
            }else {
                videoViewWatchAds.setVideoURI(Uri.parse(adsUrl.get(0)));
            }



            videoViewWatchAds.start();

            videoViewWatchAds.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    progressDialog.dismiss();
                }
            });

            videoViewWatchAds.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {

                    user.getWatchedToday().add(adsUrl.get(0));

                    if (adsArrayList.size() == user.getWatchedToday().size()){
                        firebaseFirestore.collection("users").document(firebaseUser.getUid()).update("watched",true);
                    }

                    firebaseFirestore.collection("users").document(firebaseUser.getUid()).update("watchedToday",user.getWatchedToday());

                    for (Ads ads : adsArrayList
                    ) {
                        if (ads.getUrl().equals(adsUrl.get(0))){
                            quizads = ads;
                            quizads.setWatchCount(quizads.getWatchCount()+1);
                            firebaseFirestore.collection("ads").document(quizads.getId()).update("watchCount",quizads.getWatchCount());
                        }
                    }

                    Intent intent = new Intent(getActivity(),QuizActivity.class);
                    intent.putExtra("quizads", quizads);
                    startActivity(intent);
                    CustomIntent.customType(getContext(),"fadein-to-fadeout");
                    getActivity().finish();
                }
            });
        }else {
            progressDialog.dismiss();
            adsLimit();
        }
    }

    private void adsLimit() {
        firebaseFirestore.collection("users").document(firebaseUser.getUid()).update("watchedToday",new ArrayList<>());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false);
        builder.setTitle("WATCH ADS");
        builder.setMessage("You've reached your daily ad watch limit");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(getActivity(),MainActivity.class);
                startActivity(intent);
                CustomIntent.customType(getContext(),"fadein-to-fadeout");
                getActivity().finish();
            }
        });
        builder.create().show();
    }
}
