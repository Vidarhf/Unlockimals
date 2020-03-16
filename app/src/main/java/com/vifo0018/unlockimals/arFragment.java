package com.vifo0018.unlockimals;

import android.net.Uri;
import android.util.Log;


import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * arFragment
 * <p>
 * Augmented Reality scene from camera-feed where 3D-objects can be placed.
 *
 * @author Vidar Häggström Fordell, vifo0018
 * @version 1.0
 * @since 2019-08-30
 */

public class arFragment extends ArFragment {

    @Override
    protected Config getSessionConfiguration(Session session) {

        getPlaneDiscoveryController().setInstructionView(null);

        Config config = new Config(session); //Session controls the lifecycle of AR in ARcore
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(config);
        this.getArSceneView().setupSession(session); //Attach session to current sceneview

        if (((MainActivity)getActivity()).setupAugmentedImageDb(config, session)){
            Log.d("SetupAugImgDb", "Success");
        }
        else {
            Log.e("SetupAugImgDb", "Failed to setup Db");
        }

        return config;
    }

    /**
     * Place ARobject unto scene.
     *
     * @param anchor trackable position + orientation of object i.e. where user tapped
     * @param obj 3D-object to render on top of achor
     */
    public void placeObject(Anchor anchor, Uri obj) {
        ModelRenderable.builder()
                .setSource(this.getContext(), obj).build().thenAccept(modelRenderable ->addNodeToScene(this, anchor, modelRenderable))
                .exceptionally((throwable -> {
                    Log.d("arFragment", "Error placing object");
                    return null;
                }));
    }

    /**
     * Create and select a node with renderable that can be translated(moved), rotated and scaled.
     *
     * @param arFragment scene to which node will be added
     * @param anchor trackable position + orientation to add node unto
     * @param modelRenderable the renderable which will be on anchor Node
     */
    private void addNodeToScene(arFragment arFragment, Anchor anchor, ModelRenderable modelRenderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode.setRenderable(modelRenderable);
        transformableNode.setParent(anchorNode);
        arFragment.getArSceneView().getScene().addChild(anchorNode);
        transformableNode.select();
    }

    /**
     * Clears all anchornodes from scene and their renderables by setting parent to null.
     *
     * @param customArFragment the scene which nodes to be cleared.
     */
    public void clearAllAnchors(arFragment customArFragment) {

        Collection<Node> nodeList = new LinkedList<>(
                (customArFragment.getArSceneView().getScene().getChildren()));

        Iterator iterator = nodeList.iterator();

        while (iterator.hasNext()){
            Object node = iterator.next();
            if (node instanceof AnchorNode){
                AnchorNode anchorNode = (AnchorNode) node;
                if (anchorNode.getAnchor() != null){
                    anchorNode.getAnchor().detach();
                    anchorNode.setParent(null); //Removes renderables by setting parent to null
                }
            }

        }

    }
}
