package com.vifo0018.unlockimals;

import android.graphics.Color;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.sceneform.FrameTime;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * MainActivity
 * <p>
 * Activity where Unlockimals is run. Majority of screen is a camera-feed where an AR-scene
 * is created. Images can be scanned, planes detected, 3D-objects placed/rotated/scaled.
 * Bottom of screen is a row, "animal selection", where unlocked animals can be selected for
 * placement.
 *
 * @author Vidar Häggström Fordell, vifo0018
 * @version 1.0
 * @since 2019-08-30
 */

public class MainActivity extends AppCompatActivity {
    private static final String UNLOCKED_ARRAY = "unlockedArray";
    private static final String SELECTED_ANIMAL_NAME = "selectedAnimalName";

    private arFragment customArFragment;
    Collection<Animal> collectionAnimals = new LinkedList<>();
    Collection<String> collectionImageNames = new LinkedList<>();
    Collection<Anchor> collectionAnchors = new LinkedList<>();
    Collection<ImageView> animalSelectionImageViews = new LinkedList<>();

    private Uri selectedAnimalObj; //.sfb file renderable in AR-sceme
    private String selectedAnimalName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Initialize AR-scene fragment */
        customArFragment = (arFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        customArFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
        customArFragment.getPlaneDiscoveryController().hide(); //Disables standard hand-gesture icon

        /* Add animals by name, need assets (model and image to scan) and drawable
         *(icon and image to show in info) with identical names.
         * Future update to solve dynamically eg. file to read.
         */
        Animal animal1 = new Animal("Elephant");
        Animal animal2 = new Animal("Badger");
        Animal animal4 = new Animal("Lion");
        Animal animal3 = new Animal("Bison");
        Animal animal5 = new Animal("Wolf");
        Animal animal6 = new Animal("Deer");
        collectionAnimals.add(animal1);
        collectionAnimals.add(animal2);
        collectionAnimals.add(animal3);
        collectionAnimals.add(animal4);
        collectionAnimals.add(animal5);
        collectionAnimals.add(animal6);

        ImageButton openInfoButton = (ImageButton) findViewById(R.id.open_info_button);
        ImageButton clearButton = (ImageButton) findViewById(R.id.clear_button);

        initializeAnimalSelection();

        if (savedInstanceState != null) { //Being reconstructed load previous run
            boolean[] unlockedArray = savedInstanceState.getBooleanArray(UNLOCKED_ARRAY); //Array of animals "unlocked" state in order.
            selectedAnimalName = savedInstanceState.getString(SELECTED_ANIMAL_NAME);

            Iterator<Animal> iterator = collectionAnimals.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                Animal tmpAnimal = iterator.next();
                tmpAnimal.setUnlocked(unlockedArray[i]);
                i++;

                if (tmpAnimal.getName().equals(selectedAnimalName)) {
                    selectedAnimalObj = Uri.parse(tmpAnimal.getName() + ".sfb");

                    /* indicate selected animal in UI */
                    for (ImageView imgView : animalSelectionImageViews) {
                        if (imgView.getContentDescription().equals(tmpAnimal.getName())) {
                            imgView.setBackgroundColor(Color.parseColor("#F4FA58"));
                        }
                    }
                }
            }


        }

        /*------ On-click listeners --------- */

        customArFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

                    if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                            && selectedAnimalObj != null) {
                        Anchor anchor = hitResult.createAnchor();
                        customArFragment.placeObject(anchor, selectedAnimalObj);
                        collectionAnchors.add(anchor);
                    }

                }
        );

        openInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInfoScreen();

            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                customArFragment.clearAllAnchors(customArFragment);
                Toast.makeText(v.getContext(),
                        "All animals removed!",
                        Toast.LENGTH_SHORT).show();

            }
        });
    }

    /**
     * Load bitmaps from all asset images with the same name as animals and setup database
     * with these images able to be scanned.
     *
     * @param config holds settings used to configure session
     * @param session Manages AR-system state and lifecycle of AR. Main entry point of ARCore
     *                api. Allow access to frames from camera.
     * @return true or false depending on successful setup
     */
    public boolean setupAugmentedImageDb(Config config, Session session) {
        AugmentedImageDatabase augmentedImageDatabase = new AugmentedImageDatabase(session);

        Iterator<Animal> animalIt = collectionAnimals.iterator();

        while (animalIt.hasNext()) {
            String imageName = animalIt.next().getName();

            Bitmap bitmap = loadImageBitmap(imageName);

            if (bitmap == null) {
                return false;
            }

            augmentedImageDatabase.addImage(imageName, bitmap);
            collectionImageNames.add(imageName);
        }

        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    /**
     * Load bitmap from .jpg file
     *
     * @param imageName without file ending.
     * @return bitmap of image
     */
    private Bitmap loadImageBitmap(String imageName) {
        try (InputStream is = getAssets().open(imageName + ".jpg")) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e("ImageLoad", "IO Exception while loading", e);
        }
        return null;
    }

    /**
     * On every frame from camera: check if scannable image is detected. If detected proceed to unlock
     * corresponding animal for use.
     *
     * @param frameTime
     */
    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = customArFragment.getArSceneView().getArFrame(); //Essentially a screenshot

        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);
        for (AugmentedImage augmentedImage : augmentedImages) {
            if (collectionImageNames.contains(augmentedImage.getName())) {
                Iterator<Animal> iterator = collectionAnimals.iterator();
                while (iterator.hasNext()) {
                    Animal animal = iterator.next();
                    if (animal.getName().equals(augmentedImage.getName())
                            && !animal.unlocked) {
                        animal.setUnlocked(true);
                        openUnlockedScreen(animal);

                    }
                }

            }
        }

    }

    /**
     * Inflate "Unlocked screen" fragment.
     * @param animal that has been unlocked, which name and Wikipedia page to be shown.
     */
    private void openUnlockedScreen(Animal animal) {
        UnlockedScreenFragment fragment;
        fragment = UnlockedScreenFragment.newInstance(animal.getName(), animal.getWikiUrl());

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        transaction.addToBackStack(null);
        transaction.add(R.id.fragment_container, fragment, "BLANK_FRAGMENT").commit();

    }

    /**
     * Inflate information screen fragment. Shows which images are able to scan and "how to use".
     */
    private void openInfoScreen() {
        InfoScreenFragment fragment = InfoScreenFragment.newInstance();

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        transaction.addToBackStack(null);
        transaction.add(R.id.fragment_container, fragment, "BLANK_FRAGMENT").commit();

    }

    /**
     * Create bottom row menu of animals. As many options as animals will be added with icons
     * representing each one. The icon-resource has to have the same name as animal.
     */
    private void initializeAnimalSelection() {
        LinearLayout animalSelection = findViewById(R.id.animalSelection);

        Iterator<Animal> iterator = collectionAnimals.iterator();

        /* Custom parameters for imageviews to fit selection */
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        /* Create as many clickable imageViews as animals*/
        while (iterator.hasNext()) {
            Animal tmpAnimal = iterator.next();

            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(params);
            imageView.setImageResource(tmpAnimal.getImageResourceId());
            imageView.setContentDescription(tmpAnimal.getName());
            /*On-click: if animal is unlocked: change colour and reset other options
            *                                  set object to be placed in AR-scene to
            *                                  corresponding animal
            *           else: show toast that aninal is not unlocked*/
            imageView.setOnClickListener(v -> {
                if (tmpAnimal.getUnlocked()) {
                    for (ImageView imgview : animalSelectionImageViews) {
                        imgview.setBackgroundColor(Color.parseColor("#FFFFFF"));
                    }

                    selectedAnimalObj = Uri.parse(tmpAnimal.getName() + ".sfb");
                    selectedAnimalName = tmpAnimal.getName();

                    v.setBackgroundColor(Color.parseColor("#F4FA58")); //Yellow tint to indicate selected
                } else {
                    Toast.makeText(this,
                            "You have not found " + tmpAnimal.getName() + " yet",
                            Toast.LENGTH_SHORT).show();

                }

            });
            animalSelection.addView(imageView);
            animalSelectionImageViews.add(imageView);
        }


    }

    /**
     * Saves unlocked state of each animal and currently selected animal
     * @param outState
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        boolean[] unlockedArray = new boolean[collectionAnimals.size()]; //-1 eller korrekt
        int i = 0;
        Iterator<Animal> iterator = collectionAnimals.iterator();
        //Populate unlocked array
        while (iterator.hasNext()) {
            unlockedArray[i] = iterator.next().unlocked;
            i++;
        }

        outState.putBooleanArray(UNLOCKED_ARRAY, unlockedArray);
        outState.putString(SELECTED_ANIMAL_NAME, selectedAnimalName);

    }

}
