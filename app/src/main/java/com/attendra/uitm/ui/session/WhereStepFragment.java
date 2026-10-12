package com.attendra.uitm.ui.session;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;
import com.attendra.uitm.data.SeedCache;
import com.attendra.uitm.databinding.FragmentStepWhereBinding;
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
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 3, Session Venue Page (CLAUDE.md section 5). The map is OpenStreetMap
 * (osmdroid) through map/VenuePinMap, and place search is Photon.
 *
 * - Building mode (Class, Test, Club "Inside"): building dropdown, then a room
 *   dropdown (venues of that building, sorted by order). The geofence centre is
 *   the BUILDING's lat/lng: GPS can't tell rooms or floors apart, so the room
 *   is a label only. A building still at 0,0 shows "Building location not set"
 *   and blocks Continue. Otherwise the map previews the building's geofence and
 *   cannot be edited.
 * - Map pin mode (SULAM, Club "Outside"): search a place by name, tap the map,
 *   long-press and drag the pin, or "Use my location".
 * - Every type: radius slider 30–150 m; the map circle follows it.
 *
 * The map is only a picker. Check-in never uses it: the geofence check is
 * Location.distanceBetween against the saved lat/lng and radius (section 6).
 */
public class WhereStepFragment extends BaseStepFragment {

    private FragmentStepWhereBinding binding;
    /** The included view_venue_pin.xml (search, suggestions, map, buttons). */
    private ViewVenuePinBinding pinViews;
    private VenuePinMap venueMap;
    private PhotonSearch photon;
    private PlaceSuggestionAdapter suggestions;
    /** True while the app itself writes into the search box, so no search starts. */
    private boolean settingSearchText;

    /** Android's own location permission prompt (foreground only). */
    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onLocationAnswer);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStepWhereBinding.inflate(inflater, container, false);
        pinViews = binding.venuePin;
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        CreateSessionViewModel vm = vm();
        binding.txtTypeBadge.setText(SessionTypes.name(vm.type));

        boolean club = vm.isType(Session.TYPE_CLUB);
        binding.segmentInside.setVisibility(club ? View.VISIBLE : View.GONE);
        binding.btnInside.setOnClickListener(v -> setInside(true));
        binding.btnOutside.setOnClickListener(v -> setInside(false));

        // The map starts at the last pin, else at UiTM Shah Alam. showMode()
        // moves it to the building in building mode.
        double startLat = vm.pinLat != null ? vm.pinLat : OsmSetup.UITM_LAT;
        double startLng = vm.pinLng != null ? vm.pinLng : OsmSetup.UITM_LNG;
        venueMap = new VenuePinMap(pinViews.mapVenue, startLat, startLng, vm.radius);
        venueMap.setListener((lat, lng) -> {
            // Building mode also moves the pin (to the building); only a
            // pin-mode move is the lecturer's chosen location.
            if (binding == null || !vm().usesPin()) return;
            vm().pinLat = lat;
            vm().pinLng = lng;
            showPinCoords();
            refreshContinue();
        });

        bindBuildings();
        bindSearch();
        pinViews.btnMyLocation.setOnClickListener(v -> useMyLocation());

        binding.sliderRadius.setValue(vm.radius);
        binding.sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            vm().radius = Math.round(value);
            showRadius();
        });

        showMode();
        showRadius();
    }

    // ------------------------------------------------------------ building/room

    private void bindBuildings() {
        CreateSessionViewModel vm = vm();
        List<Building> list = new ArrayList<>(vm.buildings.values());
        DropdownAdapter<Building> adapter = new DropdownAdapter<>(requireContext(), list,
                b -> b.name != null ? b.name : b.id);
        binding.inputBuilding.setAdapter(adapter);
        Building chosen = vm.selectedBuilding();
        if (chosen != null) binding.inputBuilding.setText(adapter.labelOf(chosen), false);
        binding.inputBuilding.setOnItemClickListener((parent, v, position, id) -> {
            Building b = (Building) parent.getItemAtPosition(position);
            if (!b.id.equals(vm().building)) {
                vm().building = b.id;
                vm().venueId = null;
                binding.inputRoom.setText("", false);
                // Start the slider at the building's own default (60 m in the seed).
                int r = b.defaultRadius > 0 ? (int) b.defaultRadius
                        : CreateSessionViewModel.RADIUS_DEFAULT;
                vm().radius = clamp(r);
                binding.sliderRadius.setValue(vm().radius);
            }
            bindRooms();
            showBuildingState();
            refreshContinue();
        });
        bindRooms();
        showBuildingState();
    }

    private void bindRooms() {
        CreateSessionViewModel vm = vm();
        binding.layoutRoom.setEnabled(vm.building != null);
        if (vm.building == null) return;
        List<Venue> rooms = vm.roomsIn(vm.building);
        DropdownAdapter<Venue> adapter = new DropdownAdapter<>(requireContext(), rooms,
                v -> v.floor == null || v.floor.isEmpty() ? v.name
                        : getString(R.string.room_and_floor, v.name, v.floor));
        binding.inputRoom.setAdapter(adapter);
        Venue chosen = SeedCache.venue(vm.venueId);
        if (chosen != null) binding.inputRoom.setText(adapter.labelOf(chosen), false);
        binding.inputRoom.setOnItemClickListener((parent, v, position, id) -> {
            vm().venueId = ((Venue) parent.getItemAtPosition(position)).id;
            showBuildingState();
            refreshContinue();
        });
    }

    /** Error card when the building has no lat/lng yet; geofence note + map otherwise. */
    private void showBuildingState() {
        Building b = vm().selectedBuilding();
        boolean missing = b != null && !b.hasLocation();
        binding.noteNoLocation.setVisibility(missing ? View.VISIBLE : View.GONE);
        if (missing) {
            binding.txtNoLocationBody.setText(getString(R.string.where_no_location_body, b.id));
        }
        binding.noteGeofence.setVisibility(b != null && !missing ? View.VISIBLE : View.GONE);
        if (!vm().usesPin()) showBuildingMap(b);
        showRadius();
    }

    /**
     * Building mode: a read-only map centred on the building, so the lecturer
     * sees the geofence circle. Search, suggestions, "Use my location" and the
     * pin hint are hidden. Hidden until a building with a location is chosen.
     */
    private void showBuildingMap(@Nullable Building b) {
        boolean show = b != null && b.hasLocation();
        pinViews.getRoot().setVisibility(show ? View.VISIBLE : View.GONE);
        binding.txtPinCoords.setVisibility(View.GONE);
        setPinControlsVisible(false);
        if (!show) return;
        venueMap.setEditable(false);
        venueMap.setPin(b.lat, b.lng, true);
    }

    // ---------------------------------------------------------------- map pin

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
        binding.groupBuilding.setVisibility(pin ? View.GONE : View.VISIBLE);
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
        if (LocationPermission.granted(requireContext())) venueMap.showMyLocation();
        showPinCoords();
    }

    private void setPinControlsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        pinViews.tilPlaceSearch.setVisibility(v);
        pinViews.btnMyLocation.setVisibility(v);
        pinViews.tvPinHint.setVisibility(v);
        if (!visible) {
            pinViews.tvSearchStatus.setVisibility(View.GONE);
            pinViews.cardPlaceSuggestions.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ place search

    /**
     * Suggestions appear while typing. PhotonSearch waits until the lecturer
     * stops typing and needs at least 3 letters, so only a few requests are
     * sent (fair use of the free Photon server).
     */
    private void bindSearch() {
        photon = new PhotonSearch(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        suggestions = new PlaceSuggestionAdapter(this::pickPlace);
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
        pinViews.etPlaceSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

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
                if (binding != null) showSearchStatus(getString(R.string.place_search_failed));
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
        if (text != null) pinViews.etPlaceSearch.setSelection(text.length());
        settingSearchText = false;
    }

    private void showSearchStatus(String text) {
        pinViews.tvSearchStatus.setVisibility(View.VISIBLE);
        pinViews.tvSearchStatus.setText(text);
    }

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
                () -> askLocation.launch(LocationPermission.PERMISSIONS));
    }

    private void onLocationAnswer(Map<String, Boolean> result) {
        if (binding == null) return;
        if (LocationPermission.granted(requireContext())) {
            moveToMyLocation();
        } else {
            Snackbar.make(binding.getRoot(), R.string.location_permission_denied_venue,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void moveToMyLocation() {
        venueMap.showMyLocation();
        // With no GPS fix yet, the pin moves by itself when the first fix arrives.
        venueMap.movePinToMyLocation(() -> {
            if (binding != null) {
                Snackbar.make(binding.getRoot(), R.string.finding_location,
                        Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- display

    private void showPinCoords() {
        if (vm().pinLat == null) return;
        binding.txtPinCoords.setText(String.format(Locale.ROOT, "%.5f, %.5f",
                vm().pinLat, vm().pinLng));
    }

    private void showRadius() {
        CreateSessionViewModel vm = vm();
        binding.txtRadius.setText(getString(R.string.meters, vm.radius));
        venueMap.setRadius(vm.radius);
        Building b = vm.selectedBuilding();
        if (b != null) {
            binding.txtGeofenceBody.setText(getString(R.string.where_geofence_body,
                    b.name != null ? b.name : b.id, vm.radius));
        }
    }

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
        // Save what the map shows. In building mode the centre comes from the
        // building when the session is built, so only the radius is taken.
        CreateSessionViewModel vm = vm();
        if (vm.usesPin()) {
            vm.pinLat = venueMap.getLat();
            vm.pinLng = venueMap.getLng();
        }
        vm.radius = (int) Math.round(venueMap.getRadius());
        host().showStep(new WhenStepFragment());
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
