package com.attendra.uitm.data;

import com.attendra.uitm.model.Club;
import com.attendra.uitm.model.Group;
import com.attendra.uitm.model.Programme;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the static seed lists (programmes, groups, clubs) that Danny imported
 * from seed/attendra_seed.json. Each list is read once; the data rarely changes.
 *
 * These three nodes are read on the Register screen, before the user has an
 * account, so the database rules must allow reading them without sign-in.
 */
public class SeedRepository {

    public void loadProgrammes(Callback<List<Programme>> callback) {
        Db.readOnce(Db.root().child("programmes"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Programme> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Programme p = child.getValue(Programme.class);
                    if (p != null) {
                        if (p.code == null) p.code = child.getKey();
                        list.add(p);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loadGroups(Callback<List<Group>> callback) {
        Db.readOnce(Db.root().child("groups"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Group> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Group g = child.getValue(Group.class);
                    if (g != null) {
                        g.id = child.getKey();
                        list.add(g);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loadClubs(Callback<List<Club>> callback) {
        Db.readOnce(Db.root().child("clubs"), new Callback<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot snapshot) {
                List<Club> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Club c = child.getValue(Club.class);
                    if (c != null) {
                        c.id = child.getKey();
                        list.add(c);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
