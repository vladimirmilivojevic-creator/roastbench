package dev.roastbench.inventory;

import dev.roastbench.inventory.InventoryDtos.AdjustmentRequest;
import dev.roastbench.inventory.InventoryDtos.AlertDto;
import dev.roastbench.inventory.InventoryDtos.LocationDto;
import dev.roastbench.inventory.InventoryDtos.LocationRequest;
import dev.roastbench.inventory.InventoryDtos.LotDto;
import dev.roastbench.inventory.InventoryDtos.LotRequest;
import dev.roastbench.inventory.InventoryDtos.MovementDto;
import dev.roastbench.inventory.InventoryDtos.ReceiptRequest;
import dev.roastbench.inventory.InventoryDtos.StockByLocationDto;
import dev.roastbench.inventory.InventoryDtos.StockStatus;
import dev.roastbench.inventory.InventoryDtos.SummaryDto;
import dev.roastbench.inventory.InventoryDtos.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Green inventory", description = "Lots, locations, stock movements and low-stock alerts")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    // ---- lots -----------------------------------------------------------------------------------------------

    @GetMapping("/lots")
    @Operation(summary = "List green coffee lots with current stock and status")
    public List<LotDto> listLots(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String country,
                                 @RequestParam(required = false) StockStatus status,
                                 @RequestParam(defaultValue = "false") boolean includeArchived) {
        return inventory.listLots(q, country, status, includeArchived);
    }

    @GetMapping("/lots/{id}")
    public LotDto getLot(@PathVariable long id) {
        return inventory.getLot(id);
    }

    @PostMapping("/lots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a lot")
    public LotDto createLot(@Valid @RequestBody LotRequest request) {
        return inventory.createLot(request);
    }

    @PutMapping("/lots/{id}")
    public LotDto updateLot(@PathVariable long id, @Valid @RequestBody LotRequest request) {
        return inventory.updateLot(id, request);
    }

    @PostMapping("/lots/{id}/archive")
    @Operation(summary = "Archive a lot (no more movements; history is kept)")
    public LotDto archive(@PathVariable long id) {
        return inventory.setArchived(id, true);
    }

    @PostMapping("/lots/{id}/restore")
    public LotDto restore(@PathVariable long id) {
        return inventory.setArchived(id, false);
    }

    // ---- ledger ---------------------------------------------------------------------------------------------

    @GetMapping("/lots/{id}/stock")
    @Operation(summary = "Stock of a lot per location")
    public List<StockByLocationDto> stock(@PathVariable long id) {
        return inventory.stockByLocation(id);
    }

    @GetMapping("/lots/{id}/movements")
    @Operation(summary = "Stock ledger of a lot, newest first, with the balance after each movement")
    public List<MovementDto> movements(@PathVariable long id) {
        return inventory.movementsOf(id);
    }

    @PostMapping("/lots/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Receive green coffee into a location")
    public LotDto receive(@PathVariable long id, @Valid @RequestBody ReceiptRequest request) {
        return inventory.receive(id, request);
    }

    @PostMapping("/lots/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Correct stock after a count (positive or negative, reason required)")
    public LotDto adjust(@PathVariable long id, @Valid @RequestBody AdjustmentRequest request) {
        return inventory.adjust(id, request);
    }

    @PostMapping("/lots/{id}/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Move green coffee between locations")
    public LotDto transfer(@PathVariable long id, @Valid @RequestBody TransferRequest request) {
        return inventory.transfer(id, request);
    }

    // ---- locations and reports ------------------------------------------------------------------------------

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return inventory.listLocations();
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationDto createLocation(@Valid @RequestBody LocationRequest request) {
        return inventory.createLocation(request);
    }

    @GetMapping("/inventory/summary")
    @Operation(summary = "Total stock, stock value and counts of low / empty lots")
    public SummaryDto summary() {
        return inventory.summary();
    }

    @GetMapping("/inventory/alerts")
    @Operation(summary = "Lots below their minimum stock level, most urgent first")
    public List<AlertDto> alerts() {
        return inventory.alerts();
    }
}
