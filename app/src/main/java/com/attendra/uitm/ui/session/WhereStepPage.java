package com.attendra.uitm.ui.session;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.databinding.WhereStepPageBinding;
import com.attendra.uitm.databinding.ViewVenuePinBinding;
import com.attendra.uitm.map.OsmSetup;
import com.attendra.uitm.map.PhotonSearch;
import com.attendra.uitm.map.PlaceResult;
import com.attendra.uitm.map.PlaceSuggestionAdapter;
import com.attendra.uitm.map.VenuePinMap;
import com.attendra.uitm.model.Building;
import com.attendra.uitm.model.Session;
import com.attendra.uitm.model.Venue;
import com.attendra.uitm.ui.common.LocationPermission;
import com.attendra.uitm.util.DropdownAdapter;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Step 3, Session Venue Page. */
public class WhereStepPage extends BaseStepPage {

    private WhereStepPageBinding binding;
    /** The included view_venue_pin.xml (search, suggestions, map, buttons). */
    private ViewVenuePinBinding pinViews;
    private VenuePinMap venueMap;
    private PhotonSearch photon;
    private PlaceSuggestionAdapter suggestions;
    /** True while the app itself writes into the search box, so no search starts. */
    private boolean settingSearchText;

    /** Android's own location permission prompt (foreground only). */
    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> answers) {
                    onLocationAnswer(answers);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = WhereStepPageBinding.inflate(inflater, container, false);
        pinViews = binding.venuePin;
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        binding.txtTypeBadge.setText(SessionTypes.name(vm.type));

        boolean club = vm.isType(Session.TYPE_CLUB);
        if (club) {
            binding.segmentInside.setVisibility(View.VISIBLE);
        } else {
            binding.segmentInside.setVisibility(View.GONE);
        }
        binding.btnInside.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setInside(true);
            }
        });
        binding.btnOutside.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setInside(false);
            }
        });

        // The map starts at the last pin, else at UiTM Shah Alam.
        double startLat;
        if (vm.pinLat != null) {
            startLat = vm.pinLat;
        } else {
            startLat = OsmSetup.UITM_LAT;
        }
        double startLng;
        if (vm.pinLng != null) {
            startLng = vm.pinLng;
        } else {
            startLng = OsmSetup.UITM_LNG;
        }
        createMap(startLat, startLng, vm);

        bindBuildings();
        bindSearch();
        setUpControls(vm);

        showMode();
        showRadius();
    }

    /** Map with the venue pin and the check-in circle. */
    private void createMap(double startLat, double startLng, CreateSessionViewModel vm) {
        venueMap = new VenuePinMap(pinViews.mapVenue, startLat, startLng, vm.radius);
        venueMap.setListener(new VenuePinMap.Listener() {
            @Override
            public void onPinMoved(double lat, double lng) {
                // Building mode also moves the pin (to the building); only a pin-mode move is the
                // lecturer's chosen location.
                if (binding == null || !vm().usesPin()) return;
                vm().pinLat = lat;
                vm().pinLng = lng;
                showPinCoords();
                refreshContinue();
            }
        });
    }

    /** "My location" button and the radius slider. */
    private void setUpControls(CreateSessionViewModel vm) {
        pinViews.btnMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useMyLocation();
            }
        });

        binding.sliderRadius.setValue(vm.radius);
        binding.sliderRadius.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                vm().radius = Math.round(value);
                showRadius();
            }
        });
    }

    // ------------------------------------------------------------ building/room

    /** Fills the Building dropdown. */
    private void bindBuildings() {
        CreateSessionViewModel vm = vm();
        List<Building> list = new ArrayList<>(vm.buildings.values());
        List<String> labels = new ArrayList<>();
        for (Building b : list) {
            labels.add(buildingLabel(b));
        }
        binding.inputBuilding.setAdapter(new DropdownAdapter(requireContext(), labels));
        Building chosen = vm.selectedBuilding();
        if (chosen != null) {
            binding.inputBuilding.setText(buildingLabel(chosen), false);
        }
        binding.inputBuilding.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Building b = list.get(position);
                if (!b.id.equals(vm().building)) {
                    vm().building = b.id;
                    vm().venueId = null;
                    binding.inputRoom.setText("", false);
                    // Start the slider at the building's own default (60 m in the seed).
                    int r;
                    if (b.defaultRadius > 0) {
                        r = (int) b.defaultRadius;
                    } else {
                        r = CreateSessionViewModel.RADIUS_DEFAULT;
                    }
                    vm().radius = clamp(r);
                    binding.sliderRadius.setValue(vm().radius);
                }
                bindRooms();
                showBuildingState();
                refreshContinue();
            }
        });
        bindRooms();
        showBuildingState();
    }

    /** The building's name, or its ID if it has no name. */
    private String buildingLabel(Building b) {
        if (b.name != null) {
            return b.name;
        }
        return b.id;
    }

    /** The room's name with its floor, if it has one. */
    private String roomLabel(Venue v) {
        if (v.floor == null || v.floor.isEmpty()) {
            return v.name;
        }
        return getString(R.string.room_and_floor, v.name, v.floor);
    }

    /** Fills the Room dropdown with the rooms of the chosen building. */
    private void bindRooms() {
        CreateSessionViewModel vm = vm();
        binding.layoutRoom.setEnabled(vm.building != null);
        if (vm.building == null) return;
        List<Venue> rooms = vm.roomsIn(vm.building);
        List<String> labels = new ArrayList<>();
        for (Venue v : rooms) {
            labels.add(roomLabel(v));
        }
        binding.inputRoom.setAdapter(new DropdownAdapter(requireContext(), labels));
        Venue chosen = SeedCache.venue(vm.venueId);
        if (chosen != null) {
            binding.inputRoom.setText(roomLabel(chosen), false);
        }
        binding.inputRoom.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                vm().venueId = rooms.get(position).id;
                showBuildingState();
                refreshContinue();
            }
        });
    }

    /** Error card when the building has no lat/lng yet; geofence note + map otherwise. */
    private void showBuildingState() {
        Building b = vm().selectedBuilding();
        boolean missing = b != null && !b.hasLocation();
        if (missing) {
            binding.noteNoLocation.setVisibility(View.VISIBLE);
        } else {
            binding.noteNoLocation.setVisibility(View.GONE);
        }
        if (missing) {
            binding.txtNoLocationBody.setText(getString(R.string.where_no_location_body, b.id));
        }
        if (b != null && !missing) {
            binding.noteGeofence.setVisibility(View.VISIBLE);
        } else {
            binding.noteGeofence.setVisibility(View.GONE);
        }
        if (!vm().usesPin()) {
            showBuildingMap(b);
        }
        showRadius();
    }

    /**
     * Building mode: a read-only map centred on the building, so the lecturer sees the geofence
     * circle.
     */
    private void showBuildingMap(@Nullable Building b) {
        boolean show = b != null && b.hasLocation();
        if (show) {
            pinViews.getRoot().setVisibility(View.VISIBLE);
        } else {
            pinViews.getRoot().setVisibility(View.GONE);
        }
        binding.txtPinCoords.setVisibility(View.GONE);
        setPinControlsVisible(false);
        if (!show) return;
        venueMap.setEditable(false);
        venueMap.setPin(b.lat, b.lng, true);
    }

    // ---------------------------------------------------------------- map pin

    /** Club: inside the campus or outside. */
    private void setInside(boolean inside) {
        vm().clubInside = inside;
        showMode();
        refreshContinue();
    }

    /** Shows either building + room, or the search + editable map, depending on the type. */
    private void showMode() {
        CreateSessionViewModel vm = vm();
        boolean pin = vm.usesPin();
        binding.btnInside.setChecked(!pin);
        binding.btnOutside.setChecked(pin);
        if (pin) {
            binding.groupBuilding.setVisibility(View.GONE);
        } else {
            binding.groupBuilding.setVisibility(View.VISIBLE);
        }
        if (!pin) {
            showBuildingMap(vm.selectedBuilding());
            return;
        }
        if (vm.pinLat == null) {
            vm.pinLat = OsmSetup.UITM_LAT;
            vm.pinLng = OsmSetup.UITM_LNG;
        }
        pinViews.getRoot().setVisibility(View.VISIBLE);
        setPinControlsVisible(true);
        binding.txtPinCoords.setVisibility(View.VISIBLE);
        venueMap.setEditable(true);
        venueMap.setPin(vm.pinLat, vm.pinLng, true);
        setSearchText(vm.placeName);
        // Show the blue "you are here" dot straight away if allowed already.
        if (LocationPermission.granted(requireContext())) {
            venueMap.showMyLocation();
        }
        showPinCoords();
    }

    /** Shows or hides the search and pin tools. */
    private void setPinControlsVisible(boolean visible) {
        int v;
        if (visible) {
            v = View.VISIBLE;
        } else {
            v = View.GONE;
        }
        pinViews.tilPlaceSearch.setVisibility(v);
        pinViews.btnMyLocation.setVisibility(v);
        pinViews.tvPinHint.setVisibility(v);
        if (!visible) {
            pinViews.tvSearchStatus.setVisibility(View.GONE);
            pinViews.cardPlaceSuggestions.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ place search

    /** Suggestions appear while typing. */
    private void bindSearch() {
        photon = new PhotonSearch(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        suggestions = new PlaceSuggestionAdapter(new PlaceSuggestionAdapter.OnPick() {
            @Override
            public void onPick(PlaceResult place) {
                pickPlace(place);
            }
        });
        pinViews.rvPlaceSuggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        pinViews.rvPlaceSuggestions.setAdapter(suggestions);

        pinViews.etPlaceSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (settingSearchText) return;
                // The box no longer shows the picked place, so forget its name.
                vm().placeName = null;
                search(s.toString());
            }
        });
        pinViews.etPlaceSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    return true;
                }
                return false;
            }
        });
    }

    /** Searches for a place and lists the results. */
    private void search(String text) {
        boolean longEnough = text.trim().length() >= 3;
        pinViews.cardPlaceSuggestions.setVisibility(View.GONE);
        if (longEnough) {
            showSearchStatus(getString(R.string.place_searching));
        } else {
            pinViews.tvSearchStatus.setVisibility(View.GONE);
        }
        photon.searchDebounced(text, new PhotonSearch.Callback() {
            @Override
            public void onResults(List<PlaceResult> results) {
                if (binding == null || !longEnough) return;
                if (results.isEmpty()) {
                    showSearchStatus(getString(R.string.place_search_none));
                    return;
                }
                pinViews.tvSearchStatus.setVisibility(View.GONE);
                suggestions.submit(results);
                pinViews.cardPlaceSuggestions.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (binding != null) {
                    showSearchStatus(getString(R.string.place_search_failed));
                }
            }
        });
    }

    /** A suggestion was tapped: fly there, and keep its name for the session. */
    private void pickPlace(PlaceResult place) {
        vm().placeName = place.fullName();
        setSearchText(place.fullName());
        pinViews.cardPlaceSuggestions.setVisibility(View.GONE);
        pinViews.tvSearchStatus.setVisibility(View.GONE);
        hideKeyboard();
        // The listener saves the point; the lecturer can still fine-tune the pin.
        venueMap.setPin(place.lat, place.lng, true);
    }

    /** Writes into the search box without starting a new search. */
    private void setSearchText(@Nullable String text) {
        settingSearchText = true;
        pinViews.etPlaceSearch.setText(text);
        if (text != null) {
            pinViews.etPlaceSearch.setSelection(text.length());
        }
        settingSearchText = false;
    }

    /** Shows a message under the search box. */
    private void showSearchStatus(String text) {
        pinViews.tvSearchStatus.setVisibility(View.VISIBLE);
        pinViews.tvSearchStatus.setText(text);
    }

    /** Closes the keyboard. */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(pinViews.etPlaceSearch.getWindowToken(), 0);
    }

    // ---------------------------------------------------------- my location

    /** "Use my location": explain first, then ask Android, then move the pin. */
    private void useMyLocation() {
        if (LocationPermission.granted(requireContext())) {
            moveToMyLocation();
            return;
        }
        LocationPermission.explain(requireContext(), R.string.location_permission_body_venue,
                new Runnable() {
                    @Override
                    public void run() {
                        askLocation.launch(LocationPermission.PERMISSIONS);
                    }
                });
    }

    /** Runs after the user answers the location question. */
    private void onLocationAnswer(Map<String, Boolean> result) {
        if (binding == null) return;
        if (LocationPermission.granted(requireContext())) {
            moveToMyLocation();
        } else {
            Snackbar.make(binding.getRoot(), R.string.location_permission_denied_venue,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    /** Moves the pin to where the lecturer is. */
    private void moveToMyLocation() {
        venueMap.showMyLocation();
        // With no GPS fix yet, the pin moves by itself when the first fix arrives.
        venueMap.movePinToMyLocation(new Runnable() {
            @Override
            public void run() {
                if (binding != null) {
                    Snackbar.make(binding.getRoot(), R.string.finding_location,
                            Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ---------------------------------------------------------------- display

    /** Shows the pin's latitude and longitude. */
    private void showPinCoords() {
        if (vm().pinLat == null) return;
        binding.txtPinCoords.setText(String.format(Locale.ROOT, "%.5f, %.5f",
                vm().pinLat, vm().pinLng));
    }

    /** Shows the radius next to the slider and on the map. */
    private void showRadius() {
        CreateSessionViewModel vm = vm();
        binding.txtRadius.setText(getString(R.string.meters, vm.radius));
        venueMap.setRadius(vm.radius);
        Building b = vm.selectedBuilding();
        if (b != null) {
            if (b.name != null) {
                binding.txtGeofenceBody.setText(getString(R.string.where_geofence_body, b.name,
                        vm.radius));
            } else {
                binding.txtGeofenceBody.setText(getString(R.string.where_geofence_body, b.id,
                        vm.radius));
            }
        }
    }

    /** Keeps the radius between the minimum and the maximum. */
    private static int clamp(int r) {
        return Math.max(CreateSessionViewModel.RADIUS_MIN,
                Math.min(CreateSessionViewModel.RADIUS_MAX, r - r % 5));
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public void onResume() {
        super.onResume();
        venueMap.onResume();
    }

    @Override
    public void onPause() {
        venueMap.onPause();
        super.onPause();
    }

    @Override
    public int stepNumber() {
        return 3;
    }

    @Override
    public int continueLabel() {
        return R.string.continue_label;
    }

    @Override
    protected boolean canContinue() {
        return vm().whereValid();
    }

    @Override
    public void onContinue() {
        if (!canContinue()) return;
        // Save what the map shows.
        CreateSessionViewModel vm = vm();
        if (vm.usesPin()) {
            vm.pinLat = venueMap.getLat();
            vm.pinLng = venueMap.getLng();
        }
        vm.radius = (int) Math.round(venueMap.getRadius());
        host().showStep(new WhenStepPage());
    }

    @Override
    public void onDestroyView() {
        photon.shutdown();
        venueMap.onDestroyView();
        binding = null;
        pinViews = null;
        super.onDestroyView();
    }
}
